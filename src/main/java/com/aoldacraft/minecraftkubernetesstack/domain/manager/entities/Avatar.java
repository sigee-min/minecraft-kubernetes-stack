package com.aoldacraft.minecraftkubernetesstack.domain.manager.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "avatars")
public class Avatar {
    @Id
    private String uuid;
    private String email;
    private byte[] avatar;
}
