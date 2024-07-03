package com.aoldacraft.minecraftkubernetesstack.domain.manager;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.AppID;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.Avatar;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AvatarRepository extends MongoRepository<Avatar, String> {
    Optional<Avatar> findByEmailEquals(String email);
}
