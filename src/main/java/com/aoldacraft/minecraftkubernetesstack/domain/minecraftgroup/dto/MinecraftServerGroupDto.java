package com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MinecraftServerGroupDto {
    String name;
    List<String> serverIps;
}
