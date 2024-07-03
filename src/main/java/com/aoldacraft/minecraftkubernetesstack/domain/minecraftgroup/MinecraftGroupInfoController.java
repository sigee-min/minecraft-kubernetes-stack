package com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup;

import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.entities.MinecraftServerGroupInfo;
import com.aoldacraft.minecraftkubernetesstack.domain.minecraftgroup.services.MinecraftGroupInfoService;
import com.aoldacraft.minecraftkubernetesstack.util.SseEmitterHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/minecraft/groups")
@RequiredArgsConstructor
public class MinecraftGroupInfoController {

    private final MinecraftGroupInfoService minecraftGroupService;
    private final SseEmitterHandler sseEmitters;

    @GetMapping
    public ResponseEntity<List<MinecraftServerGroupInfo>> getAllGroups() {
        return ResponseEntity.ok(minecraftGroupService.getAllGroups());
    }

    @GetMapping("/{name}")
    public ResponseEntity<MinecraftServerGroupInfo> getGroup(@PathVariable String name) {
        return ResponseEntity.ok(minecraftGroupService.getGroup(name));
    }

    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> connect() {
        SseEmitter emitter = new SseEmitter();
        sseEmitters.add(emitter);
        minecraftGroupService.publishMinecraftServerGroupInfoInit(emitter);
        return ResponseEntity.ok(emitter);
    }

}
