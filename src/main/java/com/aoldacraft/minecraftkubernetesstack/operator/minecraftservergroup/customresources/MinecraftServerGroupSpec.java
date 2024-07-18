package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources;

import io.fabric8.kubernetes.api.model.ResourceRequirements;
import lombok.Data;

import java.util.List;

@Data
public class MinecraftServerGroupSpec {

    private Integer replicas = 1;
    private ResourceRequirements resourceRequirements;
    private Boolean isForce = true;

    private String memory;
    private String initMemory;
    private String maxMemory;
    private String tz;
    private Boolean enableRollingLogs;
    private Boolean enableJmx;
    private String jmxHost;
    private Boolean useAikarFlags;
    private String jvmOpts;
    private String jvmXxOpts;
    private String jvmDdOpts;
    private String extraArgs;
    private Boolean logTimestamp;

    private Boolean eula;
    private String version;
    private String motd;
    private String difficulty;
    private String icon;
    private Boolean overrideIcon;
    private Integer maxPlayers;
    private Integer maxWorldSize;
    private Boolean allowNether;
    private Boolean announcePlayerAchievements;
    private Boolean enableCommandBlock;
    private Boolean forceGamemode;
    private Boolean generateStructures;
    private Boolean hardcore;
    private Boolean snooperEnabled;
    private Integer maxBuildHeight;
    private Boolean spawnAnimals;
    private Boolean spawnMonsters;
    private Boolean spawnNpcs;
    private Integer spawnProtection;
    private Integer viewDistance;
    private String seed;
    private String mode;
    private Boolean pvp;
    private String levelType;
    private String generatorSettings;
    private String level;
    private Boolean allowFlight;
    private String serverName;
    private Integer serverPort;
    private Integer playerIdleTimeout;
    private Boolean syncChunkWrites;
    private Boolean enableStatus;
    private Integer entityBroadcastRangePercentage;
    private Integer functionPermissionLevel;
    private Integer networkCompressionThreshold;
    private Integer opPermissionLevel;
    private Boolean preventProxyConnections;
    private Boolean useNativeTransport;
    private Integer simulationDistance;
    private Boolean execDirectly;
    private Integer stopServerAnnounceDelay;
    private String proxy;
    private Boolean console;
    private Boolean gui;
    private Integer stopDuration;
    private Boolean setupOnly;
    private Boolean useFlareFlags;
    private Boolean useSimdFlags;

    // Custom Resource Pack
    private String resourcePack;
    private String resourcePackSha1;
    private Boolean resourcePackEnforce;

    // Whitelist
    private Boolean enableWhitelist;
    private String whitelist;
    private String whitelistFile;
    private Boolean overrideWhitelist;

    // RCON
    private Boolean enableRcon;
    private String rconPassword;
    private Integer rconPort;
    private Boolean broadcastRconToOps;
    private String rconCmdsStartup;
    private String rconCmdsOnConnect;
    private String rconCmdsOnDisconnect;
    private String rconCmdsLastDisconnect;

    // Auto-Pause
    private Boolean enableAutopause;
    private Integer autopauseTimeoutEst;
    private Integer autopauseTimeoutInit;
    private Integer autopauseTimeoutKn;
    private Integer autopausePeriod;
    private String autopauseKnockInterface;
    private Boolean debugAutopause;

    // Auto-Stop
    private Boolean enableAutostop;
    private Integer autostopTimeoutEst;
    private Integer autostopTimeoutInit;
    private Integer autostopPeriod;
    private Boolean debugAutostop;

    // Mods and Plugins
    private List<String> mods;
    private List<String> plugins;

    // World
    private String world;  // New field for world
}