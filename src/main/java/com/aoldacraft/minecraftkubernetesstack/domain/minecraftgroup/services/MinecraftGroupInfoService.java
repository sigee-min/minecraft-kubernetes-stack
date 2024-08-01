package com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.services;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.ManagerService;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.Manager;
import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.MinecraftServerGroupInfoRepository;
import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.dto.MinecraftServerGroupDto;
import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.entities.MinecraftServerGroupInfo;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.MinecraftServerGroupOperator;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.util.SseEmitterHandler;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MinecraftGroupInfoService implements ServerGroupInfoPublisher {

    private final Logger log = LoggerFactory.getLogger(MinecraftGroupInfoService.class);

    private final MinecraftServerGroupInfoRepository groupRepository;
    private final KubernetesClient kubernetesClient;
    private final ManagerService managerService;
    private final SseEmitterHandler sseEmitterHandler;

    public List<MinecraftServerGroupInfo> getAllGroups() {
        String username = getCurrentUsername();
        log.info("Fetching manager by email: {}", username);
        Manager manager = managerService.findByEmail(username)
                .orElseThrow(() -> {
                    log.error("Manager not found for email: {}", username);
                    return new IllegalArgumentException("Manager not found: " + username);
                });

        log.info("Syncing server groups");
        var dbGroups = syncServerGroups();

        if (manager.getRoles().contains("ROLE_ADMIN")) {
            log.info("User has ROLE_ADMIN, returning all groups");
            return dbGroups;
        } else {
            log.info("Filtering groups for user: {}", username);
            return dbGroups.stream()
                    //.filter(group -> group.getManagers().contains(manager.getUsername()))
                    .collect(Collectors.toList());
        }
    }

    public MinecraftServerGroupInfo getGroup(String name) {
        String username = getCurrentUsername();
        log.info("Fetching manager by email: {}", username);
        Manager manager = managerService.findByEmail(username)
                .orElseThrow(() -> {
                    log.error("Manager not found for email: {}", username);
                    return new IllegalArgumentException("Manager not found: " + username);
                });

        log.info("Fetching group by name: {}", name);
        MinecraftServerGroupInfo dbGroup = groupRepository.findById(name)
                .orElseThrow(() -> {
                    log.error("Group not found: {}", name);
                    return new IllegalArgumentException("Group not found: " + name);
                });

        log.info("Checking if group exists in Kubernetes: {}", name);
        boolean existsInK8s = kubernetesClient.resources(MinecraftServerGroup.class).withName(name).get() != null;
        dbGroup.setActive(existsInK8s);
        log.info("Group {} exists in Kubernetes: {}", name, existsInK8s);
        groupRepository.save(dbGroup);
        return dbGroup;
    }

    private String getCurrentUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else {
            return principal.toString();
        }
    }

    @Override
    public void publishMinecraftServerGroupInfoInit(SseEmitter sseEmitter) {
        log.info("Publishing initial Minecraft server group info");
        syncServerGroups().stream().filter(MinecraftServerGroupInfo::isActive).forEach(minecraftServerGroupInfo -> {
            try {
                log.info("Sending initial group info for group: {}", minecraftServerGroupInfo.getName());
                sseEmitter.send(
                        SseEmitter.event()
                                .name(MinecraftServerGroupOperator.LABEL_GROUP)
                                .data(MinecraftServerGroupDto.builder()
                                                .name(minecraftServerGroupInfo.getName())
                                                .serverIps(minecraftServerGroupInfo.getStatus().getPodIPs())
                                                .isForce(minecraftServerGroupInfo.getSpec().getIsForce())
                                                .build()
                                        , MediaType.APPLICATION_JSON)
                                .reconnectTime(500)
                                .build()
                );
            } catch (IOException e) {
                log.error("Error sending initial group info for group: {}", minecraftServerGroupInfo.getName(), e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void publishMinecraftServerGroupInfo(MinecraftServerGroup resource) {
        log.info("Publishing Minecraft server group info for resource: {}", resource.getMetadata().getName());
        MinecraftServerGroupInfo info = syncServerGroup(
                resource.getMetadata().getName(),
                resource.getMetadata().getNamespace());

        sseEmitterHandler.getEmitters().forEach(sseEmitter -> {
            try {
                log.info("Sending updated group info for group: {}", resource.getMetadata().getName());
                sseEmitter.send(
                        SseEmitter.event()
                                .name(MinecraftServerGroupOperator.LABEL_GROUP)
                                .data(MinecraftServerGroupDto.builder()
                                                .name(resource.getMetadata().getName())
                                                .serverIps(resource.getStatus().getPodIPs())
                                                .isForce(resource.getSpec().getIsForce())
                                                .build()
                                        , MediaType.APPLICATION_JSON)
                                .reconnectTime(500)
                                .build()
                );
            } catch (IOException e) {
                log.error("Error sending updated group info for group: {}", resource.getMetadata().getName(), e);
                throw new RuntimeException(e);
            }
        });
    }

    protected List<MinecraftServerGroupInfo> syncServerGroups() {
        log.info("Syncing server groups with Kubernetes");
        List<MinecraftServerGroup> k8sGroups = kubernetesClient.resources(MinecraftServerGroup.class).list().getItems();
        List<MinecraftServerGroupInfo> dbGroups = groupRepository.findAll();

        Map<String, MinecraftServerGroup> k8sGroupMap = k8sGroups.stream()
                .collect(Collectors.toMap(g -> g.getMetadata().getName(), g -> g));

        for (MinecraftServerGroupInfo dbGroup : dbGroups) {
            if (k8sGroupMap.containsKey(dbGroup.getName())) {
                final var k8sResource = k8sGroupMap.get(dbGroup.getName());
                if(k8sResource.getSpec().getIsForce() != dbGroup.getSpec().getIsForce()) {
                    dbGroup = MinecraftServerGroupInfo.makeFromCRD(k8sResource);
                }
                dbGroup.setActive(true);
                k8sGroupMap.remove(dbGroup.getName());
            } else {
                dbGroup.setActive(false);
            }
            log.info("Saving group info to database: {}", dbGroup.getName());
            groupRepository.save(dbGroup);
        }

        // 남아있는 K8S 그룹을 DB에 추가
        for (MinecraftServerGroup k8sGroup : k8sGroupMap.values()) {
            log.info("Adding new group from Kubernetes to database: {}", k8sGroup.getMetadata().getName());
            MinecraftServerGroupInfo newDbGroup = MinecraftServerGroupInfo.makeFromCRD(k8sGroup);
            newDbGroup.setActive(true);
            groupRepository.save(newDbGroup);
        }

        return dbGroups;
    }

    public MinecraftServerGroupInfo syncServerGroup(String name, String namespace) {
        log.info("Syncing individual server group with Kubernetes: {}", name);
        MinecraftServerGroup k8sGroup = kubernetesClient.resources(MinecraftServerGroup.class).inNamespace(namespace).withName(name).get();
        log.debug("Retrieved Kubernetes server group: {}", k8sGroup);

        MinecraftServerGroupInfo dbGroup = groupRepository.findByNameAndNamespace(name, namespace).orElseGet(() -> {
            log.info("Group not found in database, creating new entry: {}", name);
            MinecraftServerGroupInfo newGroup = MinecraftServerGroupInfo.makeFromCRD(k8sGroup);
            log.debug("New group created from CRD: {}", newGroup);
            return groupRepository.save(newGroup);
        });
        log.debug("Database group info: {}", dbGroup);

        try {
            if (dbGroup.getStatus().getPodIPs().hashCode() == k8sGroup.getStatus().getPodIPs().hashCode()) {
                log.info("No changes detected in group status, returning existing group: {}", name);
                return dbGroup;
            }
        } catch (Exception exception) {
            log.error("Error comparing group statuses for {}: {}", name, exception.getMessage());
        }

        log.info("Updating group status in database: {}", name);
        dbGroup.setStatus(k8sGroup.getStatus());
        MinecraftServerGroupInfo updatedGroup = groupRepository.save(dbGroup);
        log.debug("Updated group info: {}", updatedGroup);

        return updatedGroup;
    }

}
