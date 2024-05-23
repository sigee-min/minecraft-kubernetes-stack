package com.aoldacraft.minecraftkubernetesstack.operator;

import com.aoldacraft.minecraftkubernetesstack.operator.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.customresources.MinecraftServerGroupStatus;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@ControllerConfiguration
public class MinecraftServerGroupController implements Reconciler<MinecraftServerGroup>, EventSourceInitializer<MinecraftServerGroup>, Deleter<MinecraftServerGroup> {
    private static final String LABEL_APP = "app";
    private static final String LABEL_GROUP = "minecraftservergroup";

    private final Logger log = LoggerFactory.getLogger(MinecraftServerGroupController.class);
    private final KubernetesClient kubernetesClient;

    public MinecraftServerGroupController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<MinecraftServerGroup> context) {
        final SecondaryToPrimaryMapper<Pod> minecraftServerGroupsMatchingPodLabel =
                (Pod pod) -> context.getPrimaryCache()
                        .list(minecraftServerGroup -> minecraftServerGroup.getMetadata().getName().equals(
                                pod.getMetadata().getLabels().get("minecraftservergroup")))
                        .map(ResourceID::fromResource)
                        .collect(Collectors.toSet());

        InformerConfiguration<Pod> configuration =
                InformerConfiguration.from(Pod.class, context)
                        .withSecondaryToPrimaryMapper(minecraftServerGroupsMatchingPodLabel)
                        .build();

        return EventSourceInitializer.nameEventSources(new InformerEventSource<>(configuration, context));
    }

    @Override
    public UpdateControl<MinecraftServerGroup> reconcile(MinecraftServerGroup resource, Context<MinecraftServerGroup> context) {
        log.info("Reconciling MinecraftServerGroup: {}", resource.getMetadata().getName());

        try {
            ensurePodsExist(resource);
            updateStatus(resource);
        } catch (Exception e) {
            log.error("Error during reconciliation of MinecraftServerGroup: {}", resource.getMetadata().getName(), e);
        }

        return UpdateControl.updateResourceAndPatchStatus(resource);
    }

    @Override
    public void delete(MinecraftServerGroup resource, Context<MinecraftServerGroup> context) {
        log.info("Deleting MinecraftServerGroup: {}", resource.getMetadata().getName());
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

    private void ensurePodsExist(MinecraftServerGroup resource) {
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
                if (podNames.contains(getPodName(resource, j))) {
                    continue;
                }
                try {
                    createPod(resource, j);
                    log.info("Creating Pod {} of {}", i + 1, desiredReplicas);
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
                } catch (Exception e) {
                    log.error("Error deleting Pod for MinecraftServerGroup: {}", resource.getMetadata().getName(), e);
                }
            }
        }
    }

    private void createPod(MinecraftServerGroup resource, int index) {
        Map<String, String> labels = createLabels(resource, index);

        Pod pod = new PodBuilder()
                .editOrNewMetadata()
                .withName(getPodName(resource, index))
                .withNamespace(resource.getMetadata().getNamespace())
                .withLabels(labels)
                .endMetadata()
                .editOrNewSpec()
                .addAllToContainers(createMinecraftContainers(resource))
                .endSpec()
                .build();

        log.info("Creating Pod: {} in namespace: {}", pod.getMetadata().getName(), pod.getMetadata().getNamespace());
        kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace()).create(pod);
    }

    private Map<String, String> createLabels(MinecraftServerGroup resource, int index) {
        return Map.of(
                LABEL_APP, resource.getMetadata().getName(),
                LABEL_GROUP, resource.getMetadata().getName(),
                "pod-index", String.valueOf(index)
        );
    }

    private void updateStatus(MinecraftServerGroup resource) {
        List<Pod> pods = getPods(resource);
        Set<String> podIPs = pods.stream()
                .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .map(pod -> pod.getStatus().getPodIP())
                .collect(Collectors.toSet());

        log.info("Updating status for MinecraftServerGroup: {}. Pod IPs: {}", resource.getMetadata().getName(), podIPs);

        MinecraftServerGroupStatus status = resource.getStatus() == null ? new MinecraftServerGroupStatus() : resource.getStatus();
        status.setPodIPs(new ArrayList<>(podIPs));
        status.setState(podIPs.isEmpty() ? "Not Ready" : "Ready");
        resource.setStatus(status);
    }

    private List<Pod> getPods(MinecraftServerGroup resource) {
        List<Pod> pods = kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace())
                .withLabel(LABEL_GROUP, resource.getMetadata().getName())
                .list().getItems();
        log.info("Found {} pods for MinecraftServerGroup: {}", pods.size(), resource.getMetadata().getName());
        return pods;
    }

    private List<Container> createMinecraftContainers(MinecraftServerGroup resource) {
        Container container = new ContainerBuilder()
                .withName("minecraft")
                .withImage("itzg/minecraft-server:latest")
                .withEnv(new EnvVar("EULA", "TRUE", null))
                .withPorts(new ContainerPortBuilder().withContainerPort(25565).build())
                .withNewResourcesLike(resource.getSpec().getResourceRequirements())
                .endResources()
                .build();

        log.info("Creating container spec for Pod in MinecraftServerGroup: {}", resource.getMetadata().getName());
        return List.of(container);
    }

    private String getPodName(MinecraftServerGroup resource, int index) {
        return resource.getMetadata().getName() + "-" + index;
    }
}
