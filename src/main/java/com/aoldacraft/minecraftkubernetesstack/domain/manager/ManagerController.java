package com.aoldacraft.minecraftkubernetesstack.domain.manager;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.dto.*;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.AppID;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.Manager;
import com.aoldacraft.minecraftkubernetesstack.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerController {

    private static final Logger logger = LogManager.getLogger(ManagerController.class);
    private final JwtUtil jwtUtil;
    private final ManagerService managerService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto authenticationRequest) {
        final String email = authenticationRequest.getEmail();
        final String password = authenticationRequest.getPassword();

        logger.info("Login attempt for user: {}", email);

        try {
            // Load the user details
            UserDetails user = managerService.loadUserByUsername(email);

            // Check password
            if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
                logger.warn("Invalid username or password for user: {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
            }

            final String jwt = jwtUtil.generateToken(user.getUsername());
            ResponseCookie jwtCookie = ResponseCookie.from("MK_ACCESSTOKEN", jwt)
                    .httpOnly(true)
                    .path("/")
                    .maxAge(60*30)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                    .body(LoginResponseDto.builder().token(jwt).build());

        } catch (Exception e) {
            logger.error("Error during login for user: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("server error");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerManager(@RequestBody SignUpDto signUpParams) {
        logger.info("Register attempt for manager: {}", signUpParams.getEmail());

        try {
            managerService.registerManager(signUpParams);
            logger.info("Manager registered successfully: {}", signUpParams.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body("Manager registered successfully.");
        } catch (Exception e) {
            logger.error("Error during registration for manager: {}", signUpParams.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Secured("ROLE_ADMIN")
    @GetMapping
    public ResponseEntity<List<Manager>> listManagers() {
        logger.info("List managers request received");

        try {
            List<Manager> managers = managerService.findAll();
            logger.info("Managers listed successfully");
            return ResponseEntity.ok(managers);
        } catch (Exception e) {
            logger.error("Error listing managers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Secured("ROLE_ADMIN")
    @PatchMapping("/enable/{username}")
    public ResponseEntity<String> enableManager(@PathVariable String username) {
        logger.info("Toggle enable status for manager: {}", username);

        try {
            managerService.toggleEnabled(username);
            logger.info("Manager enabled status toggled: {}", username);
            return ResponseEntity.ok("Manager enabled status toggled.");
        } catch (Exception e) {
            logger.error("Error toggling enabled status for manager: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Secured("ROLE_ADMIN")
    @DeleteMapping("/{uuid}")
    public ResponseEntity<String> deleteManager(@PathVariable String uuid) {
        logger.info("Delete manager request uuid for username: {}", uuid);

        try {
            managerService.deleteUser(uuid);
            logger.info("Manager deleted successfully: {}", uuid);
            return ResponseEntity.ok("Manager deleted successfully.");
        } catch (Exception e) {
            logger.error("Error deleting manager: {}", uuid);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Secured("ROLE_ADMIN")
    @PatchMapping("/toggleRole/{username}")
    public ResponseEntity<String> toggleRole(@PathVariable String username) {
        logger.info("Toggle role for manager: {}", username);

        try {
            managerService.toggleRole(username);
            logger.info("Manager role toggled: {}", username);
            return ResponseEntity.ok("Manager role toggled.");
        } catch (Exception e) {
            logger.error("Error toggling role for manager: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @GetMapping("/profile/image/{uuid}")
    public ResponseEntity<byte[]> getProfileImage(@PathVariable String uuid) {
        try {
            final var res = managerService.getProfileImage(uuid);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileDto> getProfile(Principal principal) {
        final var email = principal.getName();
        logger.info("Get profile request received for email: {}", email);

        try {
            final var res = managerService.getProfileByEmail(email);
            logger.info("Profile retrieved successfully for email: {}", email);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            logger.error("Error retrieving profile for username: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/profile")
    public ResponseEntity<String> updateProfile(@RequestBody ProfileUpdateDto profileUpdateDto,
                                                Principal principal) {
        final var email = principal.getName();
        logger.info("Update profile request received for username: {}", email);

        try {
            if (profileUpdateDto.getFirstName().length() > 20 || profileUpdateDto.getLastName().length() > 20) {
                logger.warn("Name too long for username: {}", email);
                return ResponseEntity.badRequest().body("Name must be 15 characters or less.");
            }
            if (profileUpdateDto.getFirstName().length() < 2 || profileUpdateDto.getLastName().length() < 2) {
                logger.warn("Name too short for username: {}", email);
                return ResponseEntity.badRequest().body("Name must be 2 characters or more.");
            }

            managerService.updateProfile(email, profileUpdateDto);
            logger.info("Profile updated successfully for username: {}", email);
            return ResponseEntity.ok("Profile updated successfully.");
        } catch (IOException e) {
            logger.error("Error updating profile image for username: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating profile image");
        } catch (Exception e) {
            logger.error("Error updating profile for username: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }
    @PatchMapping("/profile")
    public ResponseEntity<String> updatePassword(@RequestBody PasswordUpdateDto passwordUpdateDto, Principal principal) {
        final var email = principal.getName();
        try {
            if (passwordUpdateDto.getPassword().length() > 20) {
                logger.warn("password too long for password: {}", email);
                return ResponseEntity.badRequest().body("Password must be 15 characters or less.");
            }
            if (passwordUpdateDto.getPassword().length() < 2 ) {
                logger.warn("password too short for password: {}", email);
                return ResponseEntity.badRequest().body("Password must be 2 characters or more.");
            }

            managerService.updatePassword(email, passwordUpdateDto);
            logger.info("Password updated successfully: {}", email);
            return ResponseEntity.ok("Password updated successfully.");
        } catch (Exception e) {
            logger.error("Error updating profile for username: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }
    @PostMapping("/profile/image")
    public ResponseEntity<String> updateProfileImage(@RequestParam("image") MultipartFile imageFile, Principal principal) {
        final var email = principal.getName();
        logger.info("Update profile request received for username: {}", email);

        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                if (imageFile.getSize() > 1048576) {
                    logger.warn("File size too large for username: {}", email);
                    return ResponseEntity.badRequest().body("File size must be less than 1MB.");
                }

                if (!"image/png".equals(imageFile.getContentType())) {
                    logger.warn("Invalid file type for username: {}", email);
                    return ResponseEntity.badRequest().body("Only PNG files are allowed.");
                }
            }

            managerService.updateProfileImage(email, imageFile);
            logger.info("Profile updated successfully for username: {}", email);
            return ResponseEntity.ok("Profile updated successfully.");
        } catch (IOException e) {
            logger.error("Error updating profile image for username: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating profile image");
        } catch (Exception e) {
            logger.error("Error updating profile for username: {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // New AppID endpoints

    @GetMapping("/profile/appIds")
    public ResponseEntity<List<AppID>> getAppIDs(Principal principal) {
        String username = principal.getName();
        logger.info("Get AppIDs request received for username: {}", username);

        try {
            List<AppID> appIDs = managerService.getAppIDs(username);
            logger.info("AppIDs retrieved successfully for username: {}", username);
            return ResponseEntity.ok(appIDs);
        } catch (Exception e) {
            logger.error("Error retrieving AppIDs for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/profile/appIds")
    public ResponseEntity<String> createAppID(Principal principal) {
        String username = principal.getName();
        logger.info("Create AppID request received for username: {}", username);

        try {
            managerService.createAppID(username);
            logger.info("AppID created successfully for username: {}", username);
            return ResponseEntity.status(HttpStatus.CREATED).body("AppID created successfully.");
        } catch (IllegalStateException e) {
            logger.warn("AppID creation failed for username: {}", username, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating AppID for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @PatchMapping("/profile/appIds")
    public ResponseEntity<String> deleteAppID(@RequestBody AppIdDeleteDto appIdDeleteDto, Principal principal) {
        try {
            final var email = principal.getName();
            logger.info("AppId delete {}", appIdDeleteDto.getUuid());
            managerService.deleteAppID(email, appIdDeleteDto.getUuid());
            logger.info("AppID deleted successfully: {}", appIdDeleteDto.getUuid());
            return ResponseEntity.ok("AppID deleted successfully.");
        } catch (Exception e) {
            logger.error("Error deleting AppID: {}", appIdDeleteDto.getUuid(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

}
