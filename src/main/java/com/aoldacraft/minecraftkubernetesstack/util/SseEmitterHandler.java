package com.aoldacraft.minecraftkubernetesstack.util;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Component
public class SseEmitterHandler {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Logger log = LoggerFactory.getLogger(SseEmitterHandler.class);

    public SseEmitter add(SseEmitter emitter) {
        log.info("Adding new SseEmitter: {}", emitter);
        this.emitters.add(emitter);

        emitter.onCompletion(() -> {
            log.info("Emitter completed: {}", emitter);
            this.emitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.warn("Emitter timeout: {}", emitter);
            emitter.complete();
            this.emitters.remove(emitter);
        });

        log.info("Emitter added successfully: {}", emitter);
        return emitter;
    }

}
