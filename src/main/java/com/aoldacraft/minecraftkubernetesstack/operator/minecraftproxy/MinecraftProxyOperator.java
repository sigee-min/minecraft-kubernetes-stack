package com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources.MinecraftProxy;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources.MinecraftProxyStatus;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.utils.ProxyPodUtil;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.utils.ProxyServiceUtil;
import io.fabric8.kubernetes.api.model.Pod;
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
public class MinecraftProxyOperator implements Reconciler<MinecraftProxy>, EventSourceInitializer<MinecraftProxy>, Deleter<MinecraftProxy> {
  private static final String LABEL_GROUP = "minecraftproxy";
  private final Logger log = LoggerFactory.getLogger(MinecraftProxyOperator.class);
  private final KubernetesClient kubernetesClient;

  public MinecraftProxyOperator(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<MinecraftProxy> context) {
    final SecondaryToPrimaryMapper<Pod> minecraftProxyMatchingPodLabel =
            (Pod pod) -> context.getPrimaryCache()
                    .list(minecraftProxy -> minecraftProxy.getMetadata().getName().equals(
                            pod.getMetadata().getLabels().get(LABEL_GROUP)))
                    .map(ResourceID::fromResource)
                    .collect(Collectors.toSet());

    InformerConfiguration<Pod> configuration =
            InformerConfiguration.from(Pod.class, context)
                    .withSecondaryToPrimaryMapper(minecraftProxyMatchingPodLabel)
                    .build();

    return EventSourceInitializer.nameEventSources(new InformerEventSource<>(configuration, context));
  }

  @Override
  public UpdateControl<MinecraftProxy> reconcile(MinecraftProxy resource, Context<MinecraftProxy> context) {
    log.info("Reconciling MinecraftProxy: {}", resource.getMetadata().getName());

    try {
      boolean specChanged = !Objects.equals(resource.getMetadata().getGeneration(), resource.getStatus().getObservedGeneration());
      if (specChanged) {
        log.info("Spec changed for MinecraftProxy: {}, deleting and recreating Pods", resource.getMetadata().getName());
        ProxyPodUtil.deleteAllPods(kubernetesClient, resource);
        ProxyServiceUtil.deleteService(kubernetesClient, resource);
        ProxyPodUtil.ensurePodsExist(kubernetesClient, resource);
      } else {
        ProxyPodUtil.ensurePodsExist(kubernetesClient, resource);
      }
      ProxyServiceUtil.ensureServiceExists(kubernetesClient, resource);
      updateStatus(resource);
      resource.getStatus().setObservedGeneration(resource.getMetadata().getGeneration());
      return UpdateControl.updateResourceAndPatchStatus(resource);
    } catch (Exception e) {
      log.error("Error during reconciliation of MinecraftProxy: {}", resource.getMetadata().getName(), e);
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public void delete(MinecraftProxy resource, Context<MinecraftProxy> context) {
    try {
      log.info("Deleting MinecraftProxy: {}", resource.getMetadata().getName());
      ProxyPodUtil.deleteAllPods(kubernetesClient, resource);
      ProxyServiceUtil.deleteService(kubernetesClient, resource);
    } catch (Exception e) {
      log.error("Error deleting for MinecraftProxy: {}", resource.getMetadata().getName(), e);
    }
  }

  private void updateStatus(MinecraftProxy resource) {
    List<Pod> pods = ProxyPodUtil.getPods(kubernetesClient, resource);
    Set<String> podIPs = pods.stream()
            .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
            .map(pod -> pod.getStatus().getPodIP())
            .collect(Collectors.toSet());

    log.info("Updating status for MinecraftProxy: {}. Pod IPs: {}", resource.getMetadata().getName(), podIPs);

    MinecraftProxyStatus status = resource.getStatus() == null ? new MinecraftProxyStatus() : resource.getStatus();
    status.setPodIPs(new ArrayList<>(podIPs));
    status.setState(podIPs.isEmpty() ? "Not Ready" : "Ready");
    resource.setStatus(status);
  }
}
