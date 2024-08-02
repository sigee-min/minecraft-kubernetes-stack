package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup;

import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.services.ServerGroupInfoPublisher;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupSpec;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroupStatus;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics.InitFile;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics.ServerData;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.utils.ResourceUtil;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.utils.ServerConfigUtil;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.utils.ServerPodUtil;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The MinecraftServerGroupOperator class is responsible for managing a Minecraft Server Group in Kubernetes.
 * It implements the Reconciler, EventSourceInitializer, and Deleter interfaces.
 */
@ControllerConfiguration
public class MinecraftServerGroupOperator implements Reconciler<MinecraftServerGroup>, EventSourceInitializer<MinecraftServerGroup>, Cleaner<MinecraftServerGroup> {
    private final Logger log = LoggerFactory.getLogger(MinecraftServerGroupOperator.class);
    private final KubernetesClient kubernetesClient;
    private final ServerGroupInfoPublisher serverGroupInfoStreamHandler;
    private final ServerPodUtil serverPodUtil;
    private final ServerConfigUtil serverConfigUtil;

    public MinecraftServerGroupOperator(KubernetesClient kubernetesClient, ServerGroupInfoPublisher service) {
        this.kubernetesClient = kubernetesClient;
        this.serverGroupInfoStreamHandler = service;
        this.serverPodUtil = new ServerPodUtil(kubernetesClient);
        this.serverConfigUtil = new ServerConfigUtil(kubernetesClient);
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<MinecraftServerGroup> context) {
        final InformerConfiguration<Pod> configurationPod =
                InformerConfiguration.from(Pod.class, context)
                        .withSecondaryToPrimaryMapper(
                                (Pod pod) -> context.getPrimaryCache()
                                        .list(minecraftServerGroup -> minecraftServerGroup.getMetadata().getName()
                                                .equals(pod.getMetadata().getLabels().get(ServerData.LABEL_GROUP)))
                                        .map(ResourceID::fromResource)
                                        .collect(Collectors.toSet())).build();

        final InformerConfiguration<ConfigMap> configurationConfigMap =
                InformerConfiguration.from(ConfigMap.class, context)
                        .withSecondaryToPrimaryMapper(
                                (ConfigMap configMap) -> context.getPrimaryCache()
                                        .list(minecraftServerGroup -> minecraftServerGroup.getMetadata().getName()
                                                .equals(configMap.getMetadata().getLabels().get(ServerData.LABEL_GROUP)))
                                        .map(ResourceID::fromResource)
                                        .collect(Collectors.toSet())).build();

        return EventSourceInitializer.nameEventSources(
                new InformerEventSource<>(configurationPod, context),
                new InformerEventSource<>(configurationConfigMap, context)
        );
    }

    @Override
    public UpdateControl<MinecraftServerGroup> reconcile(MinecraftServerGroup resource, Context<MinecraftServerGroup> context) {
        log.info("Reconciling MinecraftServerGroup: {}", resource.getMetadata().getName());
        try {
            final var tmpConfigMap = serverConfigUtil.getConfigMap(resource);
            boolean specChanged = resource.getStatus() == null ||
                    !Objects.equals(resource.getMetadata().getGeneration(), resource.getStatus().getObservedGeneration()) ||
                    tmpConfigMap == null || !Objects.equals(tmpConfigMap.getMetadata().getGeneration(), resource.getStatus().getConfigMapObservedGeneration());
            if (specChanged) {
                log.info("Changed MinecraftServerGroup: {}", resource.getMetadata().getName());
                serverPodUtil.delete(resource);
                updateStatus(resource);
            }
            Boolean serverUpdated = serverPodUtil.sync(resource);
            Boolean configUpdated = serverConfigUtil.sync(resource);
            updateStatus(resource);
            if (configUpdated || serverUpdated || specChanged) {
                return UpdateControl.updateResourceAndPatchStatus(resource);
            }
        } catch (Exception e) {
            log.error("Error during reconciliation of MinecraftServerGroup: {}", resource.getMetadata().getName(), e);
        }
        return UpdateControl.noUpdate();
    }

    private void updateStatus(MinecraftServerGroup resource) {
        MinecraftServerGroupStatus status = resource.getStatus() == null ? new MinecraftServerGroupStatus() : resource.getStatus();
        serverPodUtil.updateStatus(resource, status);
        serverConfigUtil.updateStatus(resource, status);
        resource.setStatus(status);
        serverGroupInfoStreamHandler.publishMinecraftServerGroupInfo(resource);
    }

    @Override
    public DeleteControl cleanup(MinecraftServerGroup resource, Context<MinecraftServerGroup> context) {
        log.info("Deleting MinecraftServerGroup: {}", resource.getMetadata().getName());
        serverPodUtil.delete(resource);
        serverConfigUtil.delete(resource);
        return DeleteControl.defaultDelete();
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