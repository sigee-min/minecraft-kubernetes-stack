package com.aoldacraft.minecraftkubernetesstack.operator.customresources;

import io.fabric8.kubernetes.client.CustomResource;
import lombok.Data;

import java.util.List;

@Data
public class MinecraftServerGroupStatus {
    private String state;
    private List<String> podIPs;
}
