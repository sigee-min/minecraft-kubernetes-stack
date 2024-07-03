package com.aoldacraft.minecraftkubernetesstack.domain.manager.dto;

import lombok.Data;

@Data
public class LoginRequestDto {
    private String email;
    private String password;
}
