package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics;

public record ServerData() {
  public static final String LABEL_GROUP = "mcks";
  public static final String INIT_IMAGE = "ghcr.io/sigee-min/sigee-min/minecraft-kubernetes-stack-init-container:3cbedc6";
  public static final String SERVER_IMAGE = "itzg/minecraft-server:latest";
}
