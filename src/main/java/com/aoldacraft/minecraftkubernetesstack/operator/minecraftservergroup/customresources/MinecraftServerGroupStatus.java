package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources;

import lombok.Data;

import java.util.List;

@Data
public class MinecraftServerGroupStatus {
    private String state;
    private List<String> podIPs;
    private Long observedGeneration;
}
