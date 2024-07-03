package com.aoldacraft.minecraftkubernetesstack.domain.manager;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.dto.PasswordUpdateDto;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.dto.ProfileDto;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.dto.ProfileUpdateDto;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.dto.SignUpDto;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.AppID;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.Avatar;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.Manager;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagerService implements UserDetailsService {

    private static final Logger logger = LogManager.getLogger(ManagerService.class);

    private final ManagerRepository managerRepository;
    private final AppIDRepository appIDRepository;
    private final AvatarRepository avatarRepository;
    public final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.info("Loading user by username: {}", username);
        try {
            Manager user = managerRepository.findManagerByEmail(username)
                    .orElseThrow(() -> {
                        logger.error("Manager not found with email: {}", username);
                        return new UsernameNotFoundException("Manager not found");
                    });

            logger.info("Manager found: {}, isEnabled: {}, Role: {}", user.getUsername(), user.isEnabled(), Arrays.toString(user.getAuthorities().toArray()));

            return user;
        } catch (UsernameNotFoundException e) {
            logger.error("Error in loadUserByUsername: {}", e.getMessage());
            return null;
        }
    }

    public void registerManager(SignUpDto signUpParams) {
        Assert.hasText(signUpParams.getEmail(), "Email must not be empty");
        Assert.hasText(signUpParams.getFirstName(), "First name must not be empty");
        Assert.hasText(signUpParams.getLastName(), "Last name must not be empty");
        Assert.hasText(signUpParams.getPassword(), "Password must not be empty");

        Manager manager = new Manager();
        manager.setUuid(UUID.randomUUID().toString());
        manager.setEmail(signUpParams.getEmail());
        manager.setFirstName(signUpParams.getFirstName());
        manager.setLastName(signUpParams.getLastName());
        manager.setPassword(passwordEncoder.encode(signUpParams.getPassword()));
        manager.setEnabled(true);
        manager.setRoles(Set.of("USER"));

        final Manager resManager = managerRepository.save(manager);

        Avatar avatar = new Avatar();
        avatar.setUuid(UUID.randomUUID().toString());
        avatar.setEmail(resManager.getEmail());
        avatarRepository.save(avatar);
    }

    public ProfileDto getProfileByEmail(String email) {
        Manager manager = managerRepository.findManagerByEmail(email).orElseThrow( () -> {
            logger.error("Invalid manager email: {}", email);
            return new IllegalArgumentException("Invalid manager email: " + email);
        });
        return ProfileDto.builder()
                .id(manager.getUuid())
                .firstName(manager.getFirstName())
                .lastName(manager.getLastName())
                .email(manager.getEmail())
                .avatar("/api/v1/manager/profile/image/%s".formatted(manager.getUuid()))
                .build();
    }

    public byte[] getProfileImage(String uuid) {
        Manager manager = managerRepository.findById(uuid).orElseThrow( () -> {
            logger.error("Invalid manager uuid: {}", uuid);
            return new IllegalArgumentException("Invalid manager uuid: " + uuid);
        });
        Avatar avatar = avatarRepository.findByEmailEquals(manager.getEmail()).orElseGet(() -> {
            Avatar tmp = new Avatar();
            tmp.setUuid(UUID.randomUUID().toString());
            tmp.setEmail(manager.getEmail());
            return avatarRepository.save(tmp);
        });
        return avatar.getAvatar();
    }

    public List<Manager> findAll() {
        logger.info("Retrieving all managers");
        return managerRepository.findAll();
    }

    public Optional<Manager> findByEmail(String username) {
        logger.info("Finding manager by username: {}", username);
        return managerRepository.findManagerByEmail(username);
    }

    public void deleteUser(String uuid) {
        try {
            Manager manager = managerRepository.findById(uuid)
                    .orElseThrow(() -> {
                        logger.error("Invalid manager uuid: {}", uuid);
                        return new IllegalArgumentException("Invalid manager uuid: " + uuid);
                    });
            managerRepository.deleteById(manager.getUuid());
            logger.info("Manager deleted successfully: {}", uuid);
        } catch (Exception e) {
            logger.error("Error deleting manager: {}",uuid);
            throw e;
        }
    }


    public void updateProfile(String email, ProfileUpdateDto profileUpdateDto) throws IOException {
        try {
            Manager manager = managerRepository.findManagerByEmail(email)
                    .orElseThrow(() -> {
                        logger.error("Invalid manager username: {}", email);
                        return new IllegalArgumentException("Invalid manager username: " + email);
                    });
            manager.setFirstName(profileUpdateDto.getFirstName());
            manager.setLastName(profileUpdateDto.getLastName());
            Manager updatedManager = managerRepository.save(manager);
            logger.info("Profile updated successfully for manager: {}", email);
        } catch (IllegalArgumentException e) {
            logger.error("Error updating profile for manager: {}", e.getMessage());
            throw e;
        }
    }

    public void updateProfileImage(String email, MultipartFile imageFile) throws IOException {
        try {
            Avatar avatar = avatarRepository.findByEmailEquals(email)
                    .orElseThrow(() -> {
                        logger.error("Invalid avatar email: {}", email);
                        return new IllegalArgumentException("Invalid manager email: " + email);
                    });

            if (imageFile != null && !imageFile.isEmpty()) {
                avatar.setAvatar(imageFile.getBytes());
            }

            Avatar res = avatarRepository.save(avatar);
            logger.info("Profile updated successfully for manager: {}", email);
        } catch (IllegalArgumentException | IOException e) {
            logger.error("Error updating profile for manager: {}", e.getMessage());
            throw e;
        }
    }

    public void updatePassword(String email, PasswordUpdateDto passwordUpdateDto) {
        Manager manager = managerRepository.findManagerByEmail(email)
                .orElseThrow(() -> {
                    logger.error("Invalid manager username: {}", email);
                    return new IllegalArgumentException("Invalid manager username: " + email);
                });
        manager.setPassword(passwordEncoder.encode(passwordUpdateDto.getPassword()));
        managerRepository.save(manager);
    }

    public Manager toggleEnabled(String username) {
        logger.info("Toggling enabled status for manager: {}", username);
        try {
            Manager manager = managerRepository.findById(username)
                    .orElseThrow(() -> {
                        logger.error("Invalid manager username: {}", username);
                        return new IllegalArgumentException("Invalid manager username: " + username);
                    });

            manager.setEnabled(!manager.isEnabled());
            Manager updatedManager = managerRepository.save(manager);
            logger.info("Enabled status toggled successfully for manager: {}", username);
            return updatedManager;
        } catch (IllegalArgumentException e) {
            logger.error("Error toggling enabled status for manager: {}", e.getMessage());
            throw e;
        }
    }

    public Manager toggleRole(String username) {
        logger.info("Toggling role for manager: {}", username);
        try {
            Manager manager = managerRepository.findById(username)
                    .orElseThrow(() -> {
                        logger.error("Invalid manager username: {}", username);
                        return new IllegalArgumentException("Invalid manager username: " + username);
                    });

            if (manager.getRoles().contains("ADMIN")) {
                manager.getRoles().remove("ADMIN");
            } else {
                manager.getRoles().add("ADMIN");
            }

            Manager updatedManager = managerRepository.save(manager);
            logger.info("Role toggled successfully for manager: {}", username);
            return updatedManager;
        } catch (IllegalArgumentException e) {
            logger.error("Error toggling role for manager: {}", e.getMessage());
            throw e;
        }
    }

    public List<AppID> getAppIDs(String managerUsername) {
        logger.info("Getting AppIDs for manager: {}", managerUsername);
        return appIDRepository.findAllByEmail(managerUsername);
    }

    public AppID createAppID(String email) {
        List<AppID> existingAppIDs = appIDRepository.findAllByEmail(email);
        if (existingAppIDs.size() >= 2) {
            throw new IllegalStateException("Cannot create more than 2 AppIDs for manager: " + email);
        }

        AppID appID = new AppID();
        appID.setEmail(email);
        appID.setLastAuthenticated(LocalDateTime.now());
        return appIDRepository.save(appID);
    }

    public AppID updateAppIDAuthentication(String appId) {
        AppID appID = appIDRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid AppID: " + appId));
        appID.setLastAuthenticated(LocalDateTime.now());
        return appIDRepository.save(appID);
    }

    public void deleteAppID(String email, String uuid) {
        AppID appID = appIDRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Invalid AppID: " + uuid));

        if(!appID.getEmail().equals(email)) {
            throw new IllegalArgumentException("Invalid AppID: " + uuid);
        }

        appIDRepository.delete(appID);
    }
}
