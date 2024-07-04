package com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.services;

import com.aoldacraft.minecraftkubernetesstack.operator.minecraftservergroup.customresources.MinecraftServerGroup;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ServerGroupInfoPublisher {
    void publishMinecraftServerGroupInfoInit(SseEmitter sseEmitter);
    void publishMinecraftServerGroupInfo(MinecraftServerGroup resource);
}
