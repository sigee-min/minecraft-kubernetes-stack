package com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.services;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.ManagerService;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.Manager;
import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.MinecraftServerGroupInfoRepository;
import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.dto.MinecraftServerGroupDto;
import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.entities.MinecraftServerGroupInfo;
import com.aoldacraft.minecraftkubernetesstack.operator.MinecraftServerGroupController;
import com.aoldacraft.minecraftkubernetesstack.operator.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.util.SseEmitterHandler;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MinecraftGroupInfoService implements ServerGroupInfoPublisher {

    private final MinecraftServerGroupInfoRepository groupRepository;
    private final KubernetesClient kubernetesClient;
    private final ManagerService managerService;
    private final SseEmitterHandler sseEmitterHandler;

    public List<MinecraftServerGroupInfo> getAllGroups() {
        String username = getCurrentUsername();
        Manager manager = managerService.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("Manager not found: " + username));

        var dbGroups = syncServerGroups();

        if (manager.getRoles().contains("ROLE_ADMIN")) {
            return dbGroups;
        } else {
            return dbGroups.stream()
                    //.filter(group -> group.getManagers().contains(manager.getUsername()))
                    .collect(Collectors.toList());
        }
    }

    public MinecraftServerGroupInfo getGroup(String name) {
        String username = getCurrentUsername();
        Manager manager = managerService.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("Manager not found: " + username));

        MinecraftServerGroupInfo dbGroup = groupRepository.findById(name)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + name));

        // 권한 체크
        //if (!manager.getRoles().contains("ROLE_ADMIN") && !dbGroup.getManagers().contains(manager.getUsername())) {
        //    throw new SecurityException("You do not have access to this group");
        //}

        boolean existsInK8s = kubernetesClient.resources(MinecraftServerGroup.class).withName(name).get() != null;
        dbGroup.setActive(existsInK8s);
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
        syncServerGroups().stream().filter(MinecraftServerGroupInfo::isActive).forEach(minecraftServerGroupInfo -> {
            try {
                sseEmitter.send(
                        SseEmitter.event()
                                .name(MinecraftServerGroupController.LABEL_GROUP)
                                .data(MinecraftServerGroupDto.builder()
                                                .name(minecraftServerGroupInfo.getName())
                                                .serverIps(minecraftServerGroupInfo.getStatus().getPodIPs())
                                                .build()
                                        , MediaType.APPLICATION_JSON)
                                .build()
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void publishMinecraftServerGroupInfo(MinecraftServerGroup resource) {
        MinecraftServerGroupInfo info = syncServerGroup(
                resource.getMetadata().getName(),
                resource.getMetadata().getNamespace());

        sseEmitterHandler.getEmitters().forEach(sseEmitter -> {
            try {
                sseEmitter.send(
                        SseEmitter.event()
                                .name(MinecraftServerGroupController.LABEL_GROUP)
                                .data(MinecraftServerGroupDto.builder()
                                            .name(resource.getMetadata().getName())
                                            .serverIps(resource.getStatus().getPodIPs())
                                            .build()
                                        , MediaType.APPLICATION_JSON)
                                .build()
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

    }

    protected List<MinecraftServerGroupInfo> syncServerGroups() {
        List<MinecraftServerGroup> k8sGroups = kubernetesClient.resources(MinecraftServerGroup.class).list().getItems();
        List<MinecraftServerGroupInfo> dbGroups = groupRepository.findAll();

        Map<String, MinecraftServerGroup> k8sGroupMap = k8sGroups.stream()
                .collect(Collectors.toMap(g -> g.getMetadata().getName(), g -> g));

        for (MinecraftServerGroupInfo dbGroup : dbGroups) {
            if (k8sGroupMap.containsKey(dbGroup.getName())) {
                dbGroup.setActive(true);
                k8sGroupMap.remove(dbGroup.getName());
            } else {
                dbGroup.setActive(false);
            }
            groupRepository.save(dbGroup);
        }

        // 남아있는 K8S 그룹을 DB에 추가
        for (MinecraftServerGroup k8sGroup : k8sGroupMap.values()) {
            MinecraftServerGroupInfo newDbGroup = MinecraftServerGroupInfo.makeFromCRD(k8sGroup);
            newDbGroup.setActive(true);
            groupRepository.save(newDbGroup);
        }

        return dbGroups;
    }

    public MinecraftServerGroupInfo syncServerGroup(String name, String namespace) {
        MinecraftServerGroup k8sGroup = kubernetesClient.resources(MinecraftServerGroup.class).inNamespace(namespace).withName(name).get();
        MinecraftServerGroupInfo dbGroup  = groupRepository.findByNameAndNamespace(name, namespace).orElseGet(
                () -> groupRepository.save(MinecraftServerGroupInfo.makeFromCRD(k8sGroup))
        );

        if(dbGroup.getStatus().getPodIPs().hashCode() == k8sGroup.getStatus().getPodIPs().hashCode()) {
            return dbGroup;
        }
        dbGroup.setStatus(k8sGroup.getStatus());
        groupRepository.save(dbGroup);
        return dbGroup;
    }


}
