package com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.utils;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources.MinecraftProxy;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources.MinecraftProxySpec;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

public class ProxyPodUtil {
  private static final Logger log = LoggerFactory.getLogger(ProxyPodUtil.class);
  private static final String PROXY_IMAGE = "ghcr.io/sigee-min/sigee-min/velocity-for-kubernetes:cc6a0b1";
  public static final String SECRET = "abcdabcdabcd";

  public static void ensurePodsExist(KubernetesClient kubernetesClient, MinecraftProxy resource) {
    List<Pod> existingPods = getPods(kubernetesClient, resource);
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
          createPod(kubernetesClient, resource, j);
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

  public static void createPod(KubernetesClient kubernetesClient, MinecraftProxy resource, int index) {
    Map<String, String> labels = Map.of(
            "minecraftproxy", resource.getMetadata().getName(),
            "pod-index", String.valueOf(index)
    );

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

  public static List<Pod> getPods(KubernetesClient kubernetesClient, MinecraftProxy resource) {
    List<Pod> pods = kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace())
            .withLabel("minecraftproxy", resource.getMetadata().getName())
            .list().getItems();
    log.info("Found {} pods for MinecraftProxy: {}", pods.size(), resource.getMetadata().getName());
    return pods;
  }

  public static void deleteAllPods(KubernetesClient kubernetesClient, MinecraftProxy resource) {
    List<Pod> pods = getPods(kubernetesClient, resource);
    for (Pod pod : pods) {
      try {
        log.info("Deleting Pod: {}", pod.getMetadata().getName());
        kubernetesClient.resource(pod).delete();
      } catch (Exception e) {
        log.error("Error deleting Pod: {}", pod.getMetadata().getName(), e);
      }
    }
  }

  private static List<Container> createMinecraftProxyContainers(MinecraftProxy resource) {
    MinecraftProxySpec spec = resource.getSpec();

    String operatorHostAddress = "192.168.0.8";
    try {
      InetAddress inetAddress = InetAddress.getLocalHost();
      operatorHostAddress = inetAddress.getHostAddress();
    } catch (UnknownHostException e) {
      log.error("Failed to get host address", e);
    }
    operatorHostAddress = "192.168.0.8";
    String operatorServiceUrl = String.format("http://%s:8080/api/v1/minecraft/groups/connect", operatorHostAddress);

    List<EnvVar> envVars = List.of(
            new EnvVar("BIND", "0.0.0.0:%d".formatted(spec.getPort()), null),
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
            .withPorts(new ContainerPortBuilder().withContainerPort(spec.getPort()).build())
            .withNewResourcesLike(spec.getResourceRequirements())
            .endResources()
            .build();

    log.info("Creating container spec for Pod in MinecraftProxy: {}", resource.getMetadata().getName());
    return List.of(container);
  }

  private static String getPodName(MinecraftProxy resource, int index) {
    return resource.getMetadata().getName() + "-" + index;
  }
}
