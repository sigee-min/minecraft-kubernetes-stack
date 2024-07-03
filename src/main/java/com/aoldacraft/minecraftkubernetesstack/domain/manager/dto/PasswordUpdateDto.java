package com.aoldacraft.minecraftkubernetesstack.domain.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class PasswordUpdateDto {
    private String password;

    public PasswordUpdateDto() {

    }
}
