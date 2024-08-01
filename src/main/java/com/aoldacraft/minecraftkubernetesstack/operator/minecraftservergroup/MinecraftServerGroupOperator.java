package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup;

import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.services.ServerGroupInfoPublisher;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.utils.ProxyPodUtil;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupSpec;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupStatus;
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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The MinecraftServerGroupOperator class is responsible for managing a Minecraft Server Group in Kubernetes.
 * It implements the Reconciler, EventSourceInitializer, and Deleter interfaces.
 */
@ControllerConfiguration
public class MinecraftServerGroupOperator implements Reconciler<MinecraftServerGroup>, EventSourceInitializer<MinecraftServerGroup>, Deleter<MinecraftServerGroup> {
    public static final String LABEL_GROUP = "minecraftservergroup";
    private static final String INIT_IMAGE = "ghcr.io/sigee-min/sigee-min/minecraft-kubernetes-stack-init-container:3cbedc6";

    private final Logger log = LoggerFactory.getLogger(MinecraftServerGroupOperator.class);
    private final KubernetesClient kubernetesClient;
    private final ServerGroupInfoPublisher serverGroupInfoStreamHandler;

    public MinecraftServerGroupOperator(KubernetesClient kubernetesClient, ServerGroupInfoPublisher service) {
        this.kubernetesClient = kubernetesClient;
        this.serverGroupInfoStreamHandler = service;
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<MinecraftServerGroup> context) {
        final SecondaryToPrimaryMapper<Pod> minecraftServerGroupsMatchingPodLabel =
                (Pod pod) -> context.getPrimaryCache()
                        .list(minecraftServerGroup -> minecraftServerGroup.getMetadata().getName().equals(
                                pod.getMetadata().getLabels().get(LABEL_GROUP)))
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
            boolean specChanged = !Objects.equals(resource.getMetadata().getGeneration(), resource.getStatus().getObservedGeneration());
            if (specChanged) {
                log.info("Changed MinecraftServerGroup: {}", resource.getMetadata().getName());
                delete(resource, context);
                updateStatus(resource);
            }
            createOrUpdateConfigMap(resource);
            ensurePodsExist(resource);
            updateStatus(resource);
            return UpdateControl.updateResourceAndPatchStatus(resource);
        } catch (Exception e) {
            log.error("Error during reconciliation of MinecraftServerGroup: {}", resource.getMetadata().getName(), e);
        }
        return UpdateControl.noUpdate();
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
        deleteConfigMap(resource);
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
                .addAllToInitContainers(createInitContainers(resource))
                .addAllToContainers(createMinecraftContainers(resource))
                .addNewVolume()
                .withName("config-volume")
                .withEmptyDir(new EmptyDirVolumeSource())
                .endVolume()
                .addNewVolume()
                .withName("config-tmp-volume")
                .withNewConfigMap()
                .withName(getConfigMapName(resource))
                .endConfigMap()
                .endVolume()
                .endSpec()
                .build();

        log.info("Creating Pod: {} in namespace: {}", pod.getMetadata().getName(), pod.getMetadata().getNamespace());
        kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace()).create(pod);
    }

    private Map<String, String> createLabels(MinecraftServerGroup resource, int index) {
        return Map.of(
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
        status.setObservedGeneration(resource.getMetadata().getGeneration());
        status.setPodIPs(new ArrayList<>(podIPs));
        status.setState(podIPs.isEmpty() ? "Not Ready" : "Ready");
        resource.setStatus(status);
        serverGroupInfoStreamHandler.publishMinecraftServerGroupInfo(resource);
    }

    private List<Pod> getPods(MinecraftServerGroup resource) {
        List<Pod> pods = kubernetesClient.pods().inNamespace(resource.getMetadata().getNamespace())
                .withLabel(LABEL_GROUP, resource.getMetadata().getName())
                .list().getItems();
        log.info("Found {} pods for MinecraftServerGroup: {}", pods.size(), resource.getMetadata().getName());
        return pods;
    }

    private List<Container> createInitContainers(MinecraftServerGroup resource) {
        Container initContainer = new ContainerBuilder()
                .withName("init-copy-config")
                .withImage(INIT_IMAGE)
                .withVolumeMounts(
                        new VolumeMountBuilder()
                                .withName("config-volume")
                                .withMountPath("/data/config")
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
        MinecraftServerGroupSpec spec = resource.getSpec();
        Container container = new ContainerBuilder()
                .withName("minecraft")
                .withImage("itzg/minecraft-server:latest")
                .withEnv(
                        new EnvVar("TYPE", "PAPER", null),
                        new EnvVar("EULA", spec.getEula() != null ? spec.getEula().toString() : "false", null),
                        new EnvVar("ONLINE_MODE", "false", null),
                        new EnvVar("VERSION", spec.getVersion() != null ? spec.getVersion() : "LATEST", null)
                )
                .withPorts(new ContainerPortBuilder().withContainerPort(25565).build())
                .withVolumeMounts(
                        new VolumeMountBuilder()
                                .withName("config-volume")
                                .withMountPath("/data/config")
                                .withReadOnly(false)
                                .build()
                )
                .withNewResourcesLike(resource.getSpec().getResourceRequirements())
                .endResources()
                .build();

        log.info("Creating container spec for Pod in MinecraftServerGroup: {}", resource.getMetadata().getName());
        return List.of(container);
    }

    private void createOrUpdateConfigMap(MinecraftServerGroup resource) {
        ConfigMap cm = kubernetesClient.configMaps()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(getConfigMapName(resource))
                .get();

        if(cm == null) {
            return;
        }

        String spigotYml = downloadFile("https://raw.githubusercontent.com/dayyeeet/minecraft-default-configs/main/%s/spigot.yml".formatted(resource.getSpec().getVersion()));
        String paperGlobalYml = downloadFile("https://raw.githubusercontent.com/dayyeeet/minecraft-default-configs/main/%s/paper-global.yml".formatted(resource.getSpec().getVersion()));
        String paperWorldYml = downloadFile("https://raw.githubusercontent.com/dayyeeet/minecraft-default-configs/main/%s/paper-world-defaults.yml".formatted(resource.getSpec().getVersion()));

        Yaml yaml = new Yaml();
        Map<String, Object> paperGlobalConfig = yaml.load(paperGlobalYml);
        Map<String, Object> proxies = (Map<String, Object>) paperGlobalConfig.get("proxies");
        if (proxies != null) {
            Map<String, Object> velocity = (Map<String, Object>) proxies.get("velocity");
            if (velocity != null) {
                velocity.put("enabled", true);
                velocity.put("online-mode", false);
                velocity.put("secret", ProxyPodUtil.SECRET);
            }
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml modifiedYaml = new Yaml(options);
        String modifiedPaperGlobalYml = modifiedYaml.dump(paperGlobalConfig);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(getConfigMapName(resource))
                .withNamespace(resource.getMetadata().getNamespace())
                .endMetadata()
                .addToData("spigot.yml", spigotYml)
                .addToData("paper-global.yml", modifiedPaperGlobalYml)
                .addToData("paper-world-defaults.yml", paperWorldYml)
                .build();

        kubernetesClient.configMaps()
                .inNamespace(resource.getMetadata().getNamespace())
                .createOrReplace(configMap);
        log.info("Created/Updated ConfigMap for MinecraftServerGroup: {}", resource.getMetadata().getName());
    }


    private String downloadFile(String fileURL) {
        StringBuilder content = new StringBuilder();
        try {
            URL url = new URL(fileURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                content.append(in.lines().collect(Collectors.joining("\n")));
            }
        } catch (Exception e) {
            log.error("Error downloading file from URL: {}", fileURL, e);
        }
        return content.toString();
    }

    private void deleteConfigMap(MinecraftServerGroup resource) {
        kubernetesClient.configMaps()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName("minecraft-config-" + resource.getMetadata().getName())
                .delete();
        log.info("Deleted ConfigMap for MinecraftServerGroup: {}", resource.getMetadata().getName());
    }

    private String getPodName(MinecraftServerGroup resource, int index) {
        return resource.getMetadata().getName() + "-" + index;
    }

    private String getConfigMapName(MinecraftServerGroup resource) {
        return "minecraft-config-" + resource.getMetadata().getName();
    }
}


//new EnvVar("MODS", modsJson, null),
//new EnvVar("PLUGINS", pluginsJson, null),
//new EnvVar("WORLD", spec.getWorld(), null),  // New environment variable for world
/*=
                        new EnvVar("MEMORY", spec.getMemory() != null ? spec.getMemory() : "1024M", null),
                        new EnvVar("INIT_MEMORY", spec.getInitMemory() != null ? spec.getInitMemory() : "512M", null),
                        new EnvVar("MAX_MEMORY", spec.getMaxMemory() != null ? spec.getMaxMemory() : "1024M", null),
                        new EnvVar("ENABLE_ROLLING_LOGS", spec.getEnableRollingLogs() != null ? spec.getEnableRollingLogs().toString() : "false", null),
                        new EnvVar("ENABLE_JMX", spec.getEnableJmx() != null ? spec.getEnableJmx().toString() : "false", null),
                        new EnvVar("JMX_HOST", spec.getJmxHost() != null ? spec.getJmxHost() : "localhost", null),
                        new EnvVar("USE_AIKAR_FLAGS", spec.getUseAikarFlags() != null ? spec.getUseAikarFlags().toString() : "true", null),
                        new EnvVar("JVM_OPTS", spec.getJvmOpts() != null ? spec.getJvmOpts() : "", null),
                        new EnvVar("JVM_XX_OPTS", spec.getJvmXxOpts() != null ? spec.getJvmXxOpts() : "", null),
                        new EnvVar("JVM_DD_OPTS", spec.getJvmDdOpts() != null ? spec.getJvmDdOpts() : "", null),
                        new EnvVar("EXTRA_ARGS", spec.getExtraArgs() != null ? spec.getExtraArgs() : "", null),
                        new EnvVar("LOG_TIMESTAMP", spec.getLogTimestamp() != null ? spec.getLogTimestamp().toString() : "true", null),

                        new EnvVar("MOTD", spec.getMotd() != null ? spec.getMotd() : "A Minecraft Server", null),
                        new EnvVar("DIFFICULTY", spec.getDifficulty() != null ? spec.getDifficulty() : "easy", null),
                        new EnvVar("ICON", spec.getIcon() != null ? spec.getIcon() : "", null),
                        new EnvVar("OVERRIDE_ICON", spec.getOverrideIcon() != null ? spec.getOverrideIcon().toString() : "false", null),
                        new EnvVar("MAX_PLAYERS", spec.getMaxPlayers() != null ? spec.getMaxPlayers().toString() : "20", null),
                        new EnvVar("MAX_WORLD_SIZE", spec.getMaxWorldSize() != null ? spec.getMaxWorldSize().toString() : "29999984", null),
                        new EnvVar("ALLOW_NETHER", spec.getAllowNether() != null ? spec.getAllowNether().toString() : "true", null),
                        new EnvVar("ANNOUNCE_PLAYER_ACHIEVEMENTS", spec.getAnnouncePlayerAchievements() != null ? spec.getAnnouncePlayerAchievements().toString() : "true", null),
                        new EnvVar("ENABLE_COMMAND_BLOCK", spec.getEnableCommandBlock() != null ? spec.getEnableCommandBlock().toString() : "false", null),
                        new EnvVar("FORCE_GAMEMODE", spec.getForceGamemode() != null ? spec.getForceGamemode().toString() : "false", null),
                        new EnvVar("GENERATE_STRUCTURES", spec.getGenerateStructures() != null ? spec.getGenerateStructures().toString() : "true", null),
                        new EnvVar("HARDCORE", spec.getHardcore() != null ? spec.getHardcore().toString() : "false", null),
                        new EnvVar("SNOOPER_ENABLED", spec.getSnooperEnabled() != null ? spec.getSnooperEnabled().toString() : "true", null),
                        new EnvVar("MAX_BUILD_HEIGHT", spec.getMaxBuildHeight() != null ? spec.getMaxBuildHeight().toString() : "256", null),
                        new EnvVar("SPAWN_ANIMALS", spec.getSpawnAnimals() != null ? spec.getSpawnAnimals().toString() : "true", null),
                        new EnvVar("SPAWN_MONSTERS", spec.getSpawnMonsters() != null ? spec.getSpawnMonsters().toString() : "true", null),
                        new EnvVar("SPAWN_NPCS", spec.getSpawnNpcs() != null ? spec.getSpawnNpcs().toString() : "true", null),
                        new EnvVar("SPAWN_PROTECTION", spec.getSpawnProtection() != null ? spec.getSpawnProtection().toString() : "16", null),
                        new EnvVar("VIEW_DISTANCE", spec.getViewDistance() != null ? spec.getViewDistance().toString() : "10", null),
                        new EnvVar("SEED", spec.getSeed() != null ? spec.getSeed() : "", null),
                        new EnvVar("MODE", spec.getMode() != null ? spec.getMode() : "survival", null),
                        new EnvVar("PVP", spec.getPvp() != null ? spec.getPvp().toString() : "true", null),
                        new EnvVar("LEVEL_TYPE", spec.getLevelType() != null ? spec.getLevelType() : "DEFAULT", null),
                        new EnvVar("GENERATOR_SETTINGS", spec.getGeneratorSettings() != null ? spec.getGeneratorSettings() : "", null),
                        new EnvVar("LEVEL", spec.getLevel() != null ? spec.getLevel() : "world", null),
                        new EnvVar("ALLOW_FLIGHT", spec.getAllowFlight() != null ? spec.getAllowFlight().toString() : "false", null),
                        new EnvVar("SERVER_NAME", spec.getServerName() != null ? spec.getServerName() : "", null),
                        new EnvVar("SERVER_PORT", spec.getServerPort() != null ? spec.getServerPort().toString() : "25565", null),
                        new EnvVar("PLAYER_IDLE_TIMEOUT", spec.getPlayerIdleTimeout() != null ? spec.getPlayerIdleTimeout().toString() : "0", null),
                        new EnvVar("SYNC_CHUNK_WRITES", spec.getSyncChunkWrites() != null ? spec.getSyncChunkWrites().toString() : "true", null),
                        new EnvVar("ENABLE_STATUS", spec.getEnableStatus() != null ? spec.getEnableStatus().toString() : "true", null),
                        new EnvVar("ENTITY_BROADCAST_RANGE_PERCENTAGE", spec.getEntityBroadcastRangePercentage() != null ? spec.getEntityBroadcastRangePercentage().toString() : "100", null),
                        new EnvVar("FUNCTION_PERMISSION_LEVEL", spec.getFunctionPermissionLevel() != null ? spec.getFunctionPermissionLevel().toString() : "2", null),
                        new EnvVar("NETWORK_COMPRESSION_THRESHOLD", spec.getNetworkCompressionThreshold() != null ? spec.getNetworkCompressionThreshold().toString() : "256", null),
                        new EnvVar("OP_PERMISSION_LEVEL", spec.getOpPermissionLevel() != null ? spec.getOpPermissionLevel().toString() : "4", null),
                        new EnvVar("PREVENT_PROXY_CONNECTIONS", spec.getPreventProxyConnections() != null ? spec.getPreventProxyConnections().toString() : "false", null),
                        new EnvVar("USE_NATIVE_TRANSPORT", spec.getUseNativeTransport() != null ? spec.getUseNativeTransport().toString() : "true", null),
                        new EnvVar("SIMULATION_DISTANCE", spec.getSimulationDistance() != null ? spec.getSimulationDistance().toString() : "10", null),
                        new EnvVar("EXEC_DIRECTLY", spec.getExecDirectly() != null ? spec.getExecDirectly().toString() : "false", null),
                        new EnvVar("STOP_SERVER_ANNOUNCE_DELAY", spec.getStopServerAnnounceDelay() != null ? spec.getStopServerAnnounceDelay().toString() : "0", null),
                        new EnvVar("PROXY", spec.getProxy() != null ? spec.getProxy() : "", null),
                        new EnvVar("CONSOLE", spec.getConsole() != null ? spec.getConsole().toString() : "false", null),
                        new EnvVar("GUI", spec.getGui() != null ? spec.getGui().toString() : "false", null),
                        new EnvVar("STOP_DURATION", spec.getStopDuration() != null ? spec.getStopDuration().toString() : "60", null),
                        new EnvVar("SETUP_ONLY", spec.getSetupOnly() != null ? spec.getSetupOnly().toString() : "false", null),
                        new EnvVar("USE_FLARE_FLAGS", spec.getUseFlareFlags() != null ? spec.getUseFlareFlags().toString() : "false", null),
                        new EnvVar("USE_SIMD_FLAGS", spec.getUseSimdFlags() != null ? spec.getUseSimdFlags().toString() : "false", null),

                        new EnvVar("RESOURCE_PACK", spec.getResourcePack() != null ? spec.getResourcePack() : "", null),
                        new EnvVar("RESOURCE_PACK_SHA1", spec.getResourcePackSha1() != null ? spec.getResourcePackSha1() : "", null),
                        new EnvVar("RESOURCE_PACK_ENFORCE", spec.getResourcePackEnforce() != null ? spec.getResourcePackEnforce().toString() : "false", null),

                        new EnvVar("ENABLE_WHITELIST", spec.getEnableWhitelist() != null ? spec.getEnableWhitelist().toString() : "false", null),
                        new EnvVar("WHITELIST", spec.getWhitelist() != null ? spec.getWhitelist() : "", null),
                        new EnvVar("WHITELIST_FILE", spec.getWhitelistFile() != null ? spec.getWhitelistFile() : "", null),
                        new EnvVar("OVERRIDE_WHITELIST", spec.getOverrideWhitelist() != null ? spec.getOverrideWhitelist().toString() : "false", null),

                        new EnvVar("ENABLE_RCON", spec.getEnableRcon() != null ? spec.getEnableRcon().toString() : "false", null),
                        new EnvVar("RCON_PASSWORD", spec.getRconPassword() != null ? spec.getRconPassword() : "", null),
                        new EnvVar("RCON_PORT", spec.getRconPort() != null ? spec.getRconPort().toString() : "25575", null),
                        new EnvVar("BROADCAST_RCON_TO_OPS", spec.getBroadcastRconToOps() != null ? spec.getBroadcastRconToOps().toString() : "true", null),
                        new EnvVar("RCON_CMDS_STARTUP", spec.getRconCmdsStartup() != null ? spec.getRconCmdsStartup() : "", null),
                        new EnvVar("RCON_CMDS_ON_CONNECT", spec.getRconCmdsOnConnect() != null ? spec.getRconCmdsOnConnect() : "", null),
                        new EnvVar("RCON_CMDS_ON_DISCONNECT", spec.getRconCmdsOnDisconnect() != null ? spec.getRconCmdsOnDisconnect() : "", null),
                        new EnvVar("RCON_CMDS_LAST_DISCONNECT", spec.getRconCmdsLastDisconnect() != null ? spec.getRconCmdsLastDisconnect() : "", null),

                        new EnvVar("ENABLE_AUTOPAUSE", spec.getEnableAutopause() != null ? spec.getEnableAutopause().toString() : "false", null),
                        new EnvVar("AUTOPAUSE_TIMEOUT_EST", spec.getAutopauseTimeoutEst() != null ? spec.getAutopauseTimeoutEst().toString() : "3600", null),
                        new EnvVar("AUTOPAUSE_TIMEOUT_INIT", spec.getAutopauseTimeoutInit() != null ? spec.getAutopauseTimeoutInit().toString() : "600", null),
                        new EnvVar("AUTOPAUSE_TIMEOUT_KN", spec.getAutopauseTimeoutKn() != null ? spec.getAutopauseTimeoutKn().toString() : "600", null),
                        new EnvVar("AUTOPAUSE_PERIOD", spec.getAutopausePeriod() != null ? spec.getAutopausePeriod().toString() : "60", null),
                        new EnvVar("AUTOPAUSE_KNOCK_INTERFACE", spec.getAutopauseKnockInterface() != null ? spec.getAutopauseKnockInterface() : "", null),
                        new EnvVar("DEBUG_AUTOPAUSE", spec.getDebugAutopause() != null ? spec.getDebugAutopause().toString() : "false", null),

                        new EnvVar("ENABLE_AUTOSTOP", spec.getEnableAutostop() != null ? spec.getEnableAutostop().toString() : "false", null),
                        new EnvVar("AUTOSTOP_TIMEOUT_EST", spec.getAutostopTimeoutEst() != null ? spec.getAutostopTimeoutEst().toString() : "3600", null),
                        new EnvVar("AUTOSTOP_TIMEOUT_INIT", spec.getAutostopTimeoutInit() != null ? spec.getAutostopTimeoutInit().toString() : "600", null),
                        new EnvVar("AUTOSTOP_PERIOD", spec.getAutostopPeriod() != null ? spec.getAutostopPeriod().toString() : "60", null),
                        new EnvVar("DEBUG_AUTOSTOP", spec.getDebugAutostop() != null ? spec.getDebugAutostop().toString() : "false", null),*/