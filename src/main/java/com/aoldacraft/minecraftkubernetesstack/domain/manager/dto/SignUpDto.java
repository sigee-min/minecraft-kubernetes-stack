package com.aoldacraft.minecraftkubernetesstack.domain.manager.dto;

import lombok.Data;

@Data
public class SignUpDto {
    private String email;
    private String firstName;
    private String lastName;
    private String password;
}