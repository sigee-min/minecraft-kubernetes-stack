package com.aoldacraft.minecraftkubernetesstack.domain.manager.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfileDto {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String avatar;
}
