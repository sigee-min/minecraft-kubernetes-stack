package com.aoldacraft.minecraftkubernetesstack.domain.manager;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.AppID;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AppIDRepository extends MongoRepository<AppID, String> {
    List<AppID> findAllByEmail(String email);
}
