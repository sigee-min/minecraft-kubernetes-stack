package com.aoldacraft.minecraftkubernetesstack.domain.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Collection;

@Data
@Builder
public class LoginResponseDto {
    private final String token;
}
