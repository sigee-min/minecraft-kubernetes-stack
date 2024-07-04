package com.aoldacraft.minecraftkubernetesstack.operator.minecraftproxy.customresources;

import lombok.Data;

import java.util.List;

@Data
public class MinecraftProxyStatus {
    private String state;
    private List<String> podIPs;
}