package com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources.MinecraftProxy;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources.MinecraftProxySpec;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources.MinecraftProxyStatus;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

@ControllerConfiguration
public class MinecraftProxyOperator implements Reconciler<MinecraftProxy>, EventSourceInitializer<MinecraftProxy>, Deleter<MinecraftProxy> {
  public static final String LABEL_GROUP = "minecraftproxy";
  private static final String PROXY_IMAGE = "velocity:latest";// "ghcr.io/sigee-min/sigee-min/velocity-for-kubernetes:eec155d";
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
      ensurePodsExist(resource);
      updateStatus(resource);
      return UpdateControl.updateResourceAndPatchStatus(resource);
    } catch (Exception e) {
      log.error("Error during reconciliation of MinecraftProxy: {}", resource.getMetadata().getName(), e);
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public void delete(MinecraftProxy resource, Context<MinecraftProxy> context) {
    log.info("Deleting MinecraftProxy: {}", resource.getMetadata().getName());
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

  private void ensurePodsExist(MinecraftProxy resource) {
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
          log.error("Error creating Pod for MinecraftProxy: {}", resource.getMetadata().getName(), e);
        }
      }
    } else if (currentReplicas > desiredReplicas) {
      for (int i = currentReplicas - 1; i >= desiredReplicas; i--) {
        try {
          log.info("Deleting Pod {}", existingPods.get(i).getMetadata().getName());
          kubernetesClient.resource(existingPods.get(i)).delete();
        } catch (Exception e) {
          log.error("Error deleting Pod for MinecraftProxy: {}", resource.getMetadata().getName(), e);
        }
      }
    }
  }

  private void createPod(MinecraftProxy resource, int index) {
    Map<String, String> labels = createLabels(resource, index);

    Pod pod = new PodBuilder()
            .editOrNewMetadata()
            .withName(getPodName(resource, index))
            .withNamespace(resource.getMetadata().getNamespace())
            .withLabels(labels)
            .endMetadata()
            .editOrNewSpec()
            .addAllToContainers(createMinecraftProxyContainers(resource))
            .endSpec()
            .build();

    log.info("Creating Pod: {} in namespace: {}", pod.getMetadata().getName(), pod.getMetadata().getNamespace());
    kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace()).create(pod);
  }

  private Map<String, String> createLabels(MinecraftProxy resource, int index) {
    return Map.of(
            LABEL_GROUP, resource.getMetadata().getName(),
            "pod-index", String.valueOf(index)
    );
  }

  private void updateStatus(MinecraftProxy resource) {
    List<Pod> pods = getPods(resource);
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

  private List<Pod> getPods(MinecraftProxy resource) {
    List<Pod> pods = kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace())
            .withLabel(LABEL_GROUP, resource.getMetadata().getName())
            .list().getItems();
    log.info("Found {} pods for MinecraftProxy: {}", pods.size(), resource.getMetadata().getName());
    return pods;
  }

  private List<Container> createMinecraftProxyContainers(MinecraftProxy resource) {
    MinecraftProxySpec spec = resource.getSpec();

    String operatorHostAddress = "localhost";
    try {
      InetAddress inetAddress = InetAddress.getLocalHost();
      operatorHostAddress = "192.168.0.8"; //inetAddress.getHostAddress();
    } catch (UnknownHostException e) {
      log.error("Failed to get host address", e);
    }

    String operatorServiceUrl = String.format("http://%s:8080", operatorHostAddress);

    List<EnvVar> envVars = List.of(
            new EnvVar("BIND", spec.getBind(), null),
            new EnvVar("MOTD", spec.getMotd(), null),
            new EnvVar("SHOW_MAX_PLAYERS", String.valueOf(spec.getShowMaxPlayers()), null),
            new EnvVar("ONLINE_MODE", String.valueOf(spec.isOnlineMode()), null),
            new EnvVar("FORCE_KEY_AUTHENTICATION", String.valueOf(spec.isForceKeyAuthentication()), null),
            new EnvVar("PREVENT_CLIENT_PROXY_CONNECTIONS", String.valueOf(spec.isPreventClientProxyConnections()), null),
            new EnvVar("PLAYER_INFO_FORWARDING_MODE", spec.getPlayerInfoForwardingMode(), null),
            new EnvVar("VELOCITY_FORWARDING_SECRET", spec.getForwardingSecret(), null),
            new EnvVar("ANNOUNCE_FORGE", String.valueOf(spec.isAnnounceForge()), null),
            new EnvVar("KICK_EXISTING_PLAYERS", String.valueOf(spec.isKickExistingPlayers()), null),
            new EnvVar("PING_PASSTHROUGH", spec.getPingPassthrough(), null),
            new EnvVar("ENABLE_PLAYER_ADDRESS_LOGGING", String.valueOf(spec.isEnablePlayerAddressLogging()), null),
            new EnvVar("COMPRESSION_THRESHOLD", String.valueOf(spec.getCompressionThreshold()), null),
            new EnvVar("COMPRESSION_LEVEL", String.valueOf(spec.getCompressionLevel()), null),
            new EnvVar("LOGIN_RATELIMIT", String.valueOf(spec.getLoginRateLimit()), null),
            new EnvVar("CONNECTION_TIMEOUT", String.valueOf(spec.getConnectionTimeout()), null),
            new EnvVar("READ_TIMEOUT", String.valueOf(spec.getReadTimeout()), null),
            new EnvVar("HAPROXY_PROTOCOL", String.valueOf(spec.isHaproxyProtocol()), null),
            new EnvVar("TCP_FAST_OPEN", String.valueOf(spec.isTcpFastOpen()), null),
            new EnvVar("BUNGEE_PLUGIN_MESSAGE_CHANNEL", String.valueOf(spec.isBungeePluginMessageChannel()), null),
            new EnvVar("SHOW_PING_REQUESTS", String.valueOf(spec.isShowPingRequests()), null),
            new EnvVar("FAILOVER_ON_UNEXPECTED_SERVER_DISCONNECT", String.valueOf(spec.isFailoverOnUnexpectedServerDisconnect()), null),
            new EnvVar("ANNOUNCE_PROXY_COMMANDS", String.valueOf(spec.isAnnounceProxyCommands()), null),
            new EnvVar("LOG_COMMAND_EXECUTIONS", String.valueOf(spec.isLogCommandExecutions()), null),
            new EnvVar("LOG_PLAYER_CONNECTIONS", String.valueOf(spec.isLogPlayerConnections()), null),
            new EnvVar("QUERY_ENABLED", String.valueOf(spec.isQueryEnabled()), null),
            new EnvVar("QUERY_PORT", String.valueOf(spec.getQueryPort()), null),
            new EnvVar("QUERY_MAP", spec.getQueryMap(), null),
            new EnvVar("SHOW_PLUGINS", String.valueOf(spec.isShowPlugins()), null),
            new EnvVar("SSE_ENDPOINT", operatorServiceUrl, null),
            new EnvVar("JAVA_OPTS", "-XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:+UnlockExperimentalVMOptions -XX:+ParallelRefProcEnabled -XX:+AlwaysPreTouch -XX:MaxInlineLevel=15", null)
    );

    Container container = new ContainerBuilder()
            .withName("minecraft-proxy")
            .withImage(PROXY_IMAGE)
            .withEnv(envVars)
            .withPorts(new ContainerPortBuilder().withContainerPort(25565).build())
            .withNewResourcesLike(spec.getResourceRequirements())
            .endResources()
            .build();

    log.info("Creating container spec for Pod in MinecraftProxy: {}", resource.getMetadata().getName());
    return List.of(container);
  }

  private String getPodName(MinecraftProxy resource, int index) {
    return resource.getMetadata().getName() + "-" + index;
  }
}
