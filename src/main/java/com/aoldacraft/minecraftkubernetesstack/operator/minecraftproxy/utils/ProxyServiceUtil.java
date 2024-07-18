package com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.utils;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources.MinecraftProxy;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ProxyServiceUtil {
  private static final Logger log = LoggerFactory.getLogger(ProxyServiceUtil.class);

  public static void ensureServiceExists(KubernetesClient kubernetesClient, MinecraftProxy resource) {
    Service existingService = kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getMetadata().getName()).get();

    if (existingService == null) {
      createService(kubernetesClient, resource);
    } else {
      log.info("Service already exists for MinecraftProxy: {}", resource.getMetadata().getName());
    }
  }

  public static void createService(KubernetesClient kubernetesClient, MinecraftProxy resource) {
    Map<String, String> labels = Map.of(
            "minecraftproxy", resource.getMetadata().getName()
    );
    final var spec = resource.getSpec();

    Service service = new ServiceBuilder()
            .withNewMetadata()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .withLabels(labels)
            .endMetadata()
            .withNewSpec()
            .withSelector(labels)
            .withPorts(
                    new ServicePortBuilder()
                            .withName("tcp-port")
                            .withProtocol("TCP")
                            .withPort(spec.getPort())
                            .withTargetPort(new IntOrString(spec.getPort()))
                            .build(),
                    new ServicePortBuilder()
                            .withName("udp-port")
                            .withProtocol("UDP")
                            .withPort(spec.getPort())
                            .withTargetPort(new IntOrString(spec.getPort()))
                            .build()
            )
            .endSpec()
            .build();

    log.info("Creating Service: {} in namespace: {}", service.getMetadata().getName(), service.getMetadata().getNamespace());
    kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace()).create(service);
  }

  public static void deleteService(KubernetesClient kubernetesClient, MinecraftProxy resource) {
    try {
      log.info("Deleting Service: {}", resource.getMetadata().getName());
      kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace())
              .withName(resource.getMetadata().getName()).delete();
    } catch (Exception e) {
      log.error("Error deleting Service: {}", resource.getMetadata().getName(), e);
    }
  }
}
