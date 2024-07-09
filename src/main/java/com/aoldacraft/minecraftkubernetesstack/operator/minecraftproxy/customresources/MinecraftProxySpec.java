package com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources;

import io.fabric8.kubernetes.api.model.ResourceRequirements;
import lombok.Data;

@Data
public class MinecraftProxySpec {
    private int replicas=1;
    private ResourceRequirements resourceRequirements;

    private String bind = "0.0.0.0:25565";
    private String motd = "<#09add3>A Velocity Server";
    private int showMaxPlayers = 500;
    private boolean onlineMode = true;
    private boolean forceKeyAuthentication = true;
    private boolean preventClientProxyConnections = false;
    private String playerInfoForwardingMode = "MODERN";
    private String forwardingSecret = "abcdabcdabcd";
    private boolean announceForge = false;
    private boolean kickExistingPlayers = false;
    private String pingPassthrough = "DISABLED";
    private boolean enablePlayerAddressLogging = true;
    private int compressionThreshold = 256;
    private int compressionLevel = -1;
    private int loginRateLimit = 3000;
    private int connectionTimeout = 5000;
    private int readTimeout = 30000;
    private boolean haproxyProtocol = false;
    private boolean tcpFastOpen = false;
    private boolean bungeePluginMessageChannel = true;
    private boolean showPingRequests = false;
    private boolean failoverOnUnexpectedServerDisconnect = true;
    private boolean announceProxyCommands = true;
    private boolean logCommandExecutions = false;
    private boolean logPlayerConnections = true;
    private boolean queryEnabled = false;
    private int queryPort = 25577;
    private String queryMap = "Velocity";
    private boolean showPlugins = false;
}
