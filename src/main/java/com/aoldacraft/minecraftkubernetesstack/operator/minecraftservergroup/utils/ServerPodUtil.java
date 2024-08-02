package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.utils;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupSpec;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupStatus;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics.InitFile;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics.ServerData;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ServerPodUtil {
  private static final Logger log = LoggerFactory.getLogger(ServerPodUtil.class);
  private final KubernetesClient kubernetesClient;

  public Boolean sync(MinecraftServerGroup resource) {
    boolean isUpdated = false;
    List<Pod> existingPods = getPods(resource);
    int desiredReplicas = resource.getSpec().getReplicas();
    int currentReplicas = existingPods.size();
    Set<String> podNames = existingPods.stream()
            .map(pod -> pod.getMetadata().getName())
            .collect(Collectors.toSet());

    log.info("Current replicas: {}, Desired replicas: {}", currentReplicas, desiredReplicas);

    if (currentReplicas < desiredReplicas) {
      int j = 0;
      for (int i = currentReplicas; i < desiredReplicas && j < desiredReplicas; j++) {
        if (podNames.contains(ResourceUtil.getPodName(resource, j))) {
          continue;
        }
        try {
          createServer(resource, j);
          log.info("Creating Pod {} of {}", i + 1, desiredReplicas);
          isUpdated = true;
          i++;
        } catch (Exception e) {
          log.error("Error creating Pod for MinecraftServerGroup: {}", resource.getMetadata().getName(), e);
        }
      }
    } else if (currentReplicas > desiredReplicas) {
      for (int i = currentReplicas - 1; i >= desiredReplicas; i--) {
        try {
          log.info("Deleting Pod {}", existingPods.get(i).getMetadata().getName());
          kubernetesClient.resource(existingPods.get(i)).delete();
          isUpdated = true;
        } catch (Exception e) {
          log.error("Error deleting Pod for MinecraftServerGroup: {}", resource.getMetadata().getName(), e);
        }
      }
    }
    return isUpdated;
  }

  public void delete(MinecraftServerGroup resource) {
    List<Pod> pods = getPods(resource);
    for (Pod pod : pods) {
      try {
        log.info("Deleting Pod: {}", pod.getMetadata().getName());
        kubernetesClient.resource(pod).delete();
      } catch (Exception e) {
        log.error("Error deleting Pod: {}", pod.getMetadata().getName(), e);
      }
    }
  }

  public void updateStatus(MinecraftServerGroup resource, MinecraftServerGroupStatus status) {
    List<Pod> pods = getPods(resource);
    Set<String> podIPs = pods.stream()
            .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
            .map(pod -> pod.getStatus().getPodIP())
            .collect(Collectors.toSet());

    log.info("Updating status for MinecraftServerGroup: {}. Pod IPs: {}", resource.getMetadata().getName(), podIPs);
    status.setPodIPs(new ArrayList<>(podIPs));
    status.setState(podIPs.isEmpty() ? "Not Ready" : "Ready");
    status.setObservedGeneration(resource.getMetadata().getGeneration());
  }

  private void createServer(MinecraftServerGroup resource, int index) {
    Map<String, String> labels = ResourceUtil.createLabels(resource, index);

    Pod pod = new PodBuilder()
            .editOrNewMetadata()
            .withName(ResourceUtil.getPodName(resource, index))
            .withNamespace(resource.getMetadata().getNamespace())
            .withLabels(labels)
            .endMetadata()
            .editOrNewSpec()
            .addAllToInitContainers(createInitContainers(resource))
            .addAllToContainers(createMinecraftContainers(resource))
            .addNewVolume()
            .withName("volume")
            .withEmptyDir(new EmptyDirVolumeSource())
            .endVolume()
            .addNewVolume()
            .withName("config-tmp-volume")
            .withNewConfigMap()
            .withName(ResourceUtil.getConfigMapName(resource))
            .endConfigMap()
            .endVolume()
            .endSpec()
            .build();

    log.info("Creating Pod: {} in namespace: {}", pod.getMetadata().getName(), pod.getMetadata().getNamespace());
    kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace()).create(pod);
  }

  private List<Pod> getPods(MinecraftServerGroup resource) {
    List<Pod> pods = kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace())
            .withLabel(ServerData.LABEL_GROUP, resource.getMetadata().getName())
            .list().getItems();
    log.info("Found {} pods for MinecraftServerGroup: {}", pods.size(), resource.getMetadata().getName());
    return pods;
  }

  private List<Container> createInitContainers(MinecraftServerGroup resource) {
    Container initContainer = new ContainerBuilder()
            .withName("init-copy-config")
            .withImage(ServerData.INIT_IMAGE)
            .withVolumeMounts(
                    new VolumeMountBuilder()
                            .withName("volume")
                            .withMountPath("/data")
                            .build(),
                    new VolumeMountBuilder()
                            .withName("config-tmp-volume")
                            .withMountPath("/data/config-tmp")
                            .build()
            )
            .build();

    log.info("Creating init container for Pod in MinecraftServerGroup: {}", resource.getMetadata().getName());
    return List.of(initContainer);
  }


  private List<Container> createMinecraftContainers(MinecraftServerGroup resource) {
    final MinecraftServerGroupSpec spec = resource.getSpec();
    final Container container = new ContainerBuilder()
            .withName("minecraft")
            .withImage(ServerData.SERVER_IMAGE)
            .withEnv(
                    new EnvVar("TYPE", "PAPER", null),
                    new EnvVar("EULA", spec.getEula() != null ? spec.getEula().toString() : "false", null),
                    new EnvVar("ONLINE_MODE", "false", null),
                    new EnvVar("VERSION", spec.getVersion() != null ? spec.getVersion() : "LATEST", null)
            )
            .withPorts(new ContainerPortBuilder().withContainerPort(25565).build())
            .withVolumeMounts(
                    new VolumeMountBuilder()
                            .withName("volume")
                            .withMountPath("/data")
                            .withReadOnly(false)
                            .build()
            )
            .withNewResourcesLike(resource.getSpec().getResourceRequirements())
            .endResources()
            .build();

    log.info("Creating container spec for Pod in MinecraftServerGroup: {}", resource.getMetadata().getName());
    return List.of(container);
  }
}
