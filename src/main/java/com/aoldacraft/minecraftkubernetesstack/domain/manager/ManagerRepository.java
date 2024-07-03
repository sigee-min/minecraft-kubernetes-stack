package com.aoldacraft.minecraftkubernetesstack.domain.manager;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.Manager;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ManagerRepository extends MongoRepository<Manager, String> {
    Optional<Manager> findManagerByEmail(String email);
}