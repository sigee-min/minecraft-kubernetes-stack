package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MinecraftServerGroupStatus {
    private String state = "NotReady";
    private List<String> podIPs = new ArrayList<>();
    private Long observedGeneration;
    private Long configMapObservedGeneration;
}
