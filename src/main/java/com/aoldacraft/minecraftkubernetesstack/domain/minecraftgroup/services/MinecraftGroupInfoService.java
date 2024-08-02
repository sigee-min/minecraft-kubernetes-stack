package com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.services;


import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.dto.MinecraftServerGroupDto;
import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.entities.MinecraftServerGroupInfo;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.statics.ServerData;
import com.aoldacraft.minecraftkubernetesstack.util.SseEmitterHandler;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MinecraftGroupInfoService implements ServerGroupInfoPublisher {

    private final Logger log = LoggerFactory.getLogger(MinecraftGroupInfoService.class);

    private final KubernetesClient kubernetesClient;
    private final SseEmitterHandler sseEmitterHandler;

    public List<MinecraftServerGroupInfo> getAllGroups() {
        return getServerGroups();
    }

    @Override
    public void publishMinecraftServerGroupInfoInit(SseEmitter sseEmitter) {
        log.info("Publishing initial Minecraft server group info");
        getServerGroups().stream().filter(MinecraftServerGroupInfo::isActive).forEach(minecraftServerGroupInfo -> {
            try {
                log.info("Sending initial group info for group: {}", minecraftServerGroupInfo.getName());
                sseEmitter.send(
                        SseEmitter.event()
                                .name(ServerData.LABEL_GROUP)
                                .data(MinecraftServerGroupDto.builder()
                                                .name(minecraftServerGroupInfo.getName())
                                                .serverIps(minecraftServerGroupInfo.getStatus().getPodIPs())
                                                .isForce(minecraftServerGroupInfo.getSpec().getIsForce())
                                                .build()
                                        , MediaType.APPLICATION_JSON)
                                .reconnectTime(5000)
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
        MinecraftServerGroupInfo info = getServerGroup(
                resource.getMetadata().getName(),
                resource.getMetadata().getNamespace());

        sseEmitterHandler.getEmitters().forEach(sseEmitter -> {
            try {
                log.info("Sending updated group info for group: {}", resource.getMetadata().getName());
                sseEmitter.send(
                        SseEmitter.event()
                                .name(ServerData.LABEL_GROUP)
                                .data(MinecraftServerGroupDto.builder()
                                                .name(resource.getMetadata().getName())
                                                .serverIps(resource.getStatus().getPodIPs())
                                                .isForce(resource.getSpec().getIsForce())
                                                .build()
                                        , MediaType.APPLICATION_JSON)
                                .reconnectTime(5000)
                                .build()
                );
            } catch (IOException e) {
                log.error("Error sending updated group info for group: {}", resource.getMetadata().getName(), e);
                throw new RuntimeException(e);
            }
        });
    }

    protected List<MinecraftServerGroupInfo> getServerGroups() {
        return kubernetesClient.resources(MinecraftServerGroup.class).list().getItems()
                .stream().map(MinecraftServerGroupInfo::makeFromCRD)
                .collect(Collectors.toList());
    }

    public MinecraftServerGroupInfo getServerGroup(String name, String namespace) {
         return MinecraftServerGroupInfo.makeFromCRD(kubernetesClient.resources(MinecraftServerGroup.class).inNamespace(namespace).withName(name).get());
    }

}
