package com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources;

import io.fabric8.kubernetes.api.model.ResourceRequirements;
import lombok.Data;

@Data
public class MinecraftServerGroupSpec {
    private String version;
    private int replicas;
    private ResourceRequirements resourceRequirements;
}
