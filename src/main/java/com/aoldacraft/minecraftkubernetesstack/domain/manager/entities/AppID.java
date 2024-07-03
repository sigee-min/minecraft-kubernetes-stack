package com.aoldacraft.minecraftkubernetesstack.domain.manager.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Document(collection = "appIds")
@Data
public class AppID {

    @Id
    private String uuid;
    private String token = generateBase64Hash();
    private String email;
    private LocalDateTime lastAuthenticated;

    private String generateBase64Hash() {
        try {
            // Create a large enough unique identifier
            StringBuilder uuidBuilder = new StringBuilder();
            while (uuidBuilder.length() < 128) {  // Ensures sufficient length for the final hash
                uuidBuilder.append(UUID.randomUUID().toString());
            }

            // Use SHA-256 algorithm to hash the combined UUIDs
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(uuidBuilder.toString().getBytes());

            // Encode the hash using Base64
            StringBuilder base64Encoded = new StringBuilder(Base64.getEncoder().encodeToString(hash));

            // Ensure the Base64 string is at least 100 characters
            while (base64Encoded.length() < 100) {
                base64Encoded.append(Base64.getEncoder().encodeToString(digest.digest(base64Encoded.toString().getBytes())));
            }

            // Trim the Base64 string to exactly 100 characters
            return base64Encoded.substring(0, 100);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating hash", e);
        }
    }
}
