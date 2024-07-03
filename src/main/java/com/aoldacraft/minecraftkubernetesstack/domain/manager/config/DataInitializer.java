package com.aoldacraft.minecraftkubernetesstack.domain.manager.config;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.ManagerController;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.ManagerRepository;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.Manager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final ManagerRepository managerRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        logger.info("DataInitializer 시작");

        try {
            if (managerRepository.findManagerByEmail("admin@example.com").isEmpty()) {
                logger.info("관리자 계정이 존재하지 않음. 새 관리자 계정을 생성합니다.");
                Manager admin = new Manager();
                admin.setUuid(UUID.randomUUID().toString());
                admin.setEmail("admin@example.com");
                admin.setFirstName("admin");
                admin.setLastName("admin");
                admin.setPassword(passwordEncoder.encode("password"));
                admin.setEnabled(true);
                admin.setRoles(Set.of("ADMIN", "USER"));

                managerRepository.save(admin);
                logger.info("관리자 계정 생성 완료: {}", admin);
            } else {
                logger.info("관리자 계정이 이미 존재합니다.");
            }
        } catch (Exception e) {
            logger.error("DataInitializer 실행 중 오류 발생", e);
        }

        logger.info("DataInitializer 완료");
    }
}

