package com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup;

import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.entities.MinecraftServerGroupInfo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MinecraftServerGroupInfoRepository extends MongoRepository<MinecraftServerGroupInfo, String> {
    Optional<MinecraftServerGroupInfo> findByNameAndNamespace(String name, String namespace);
}
