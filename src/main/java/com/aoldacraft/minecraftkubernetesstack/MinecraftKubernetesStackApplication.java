package com.aoldacraft.minecraftkubernetesstack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class MinecraftKubernetesStackApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinecraftKubernetesStackApplication.class, args);
    }

}
