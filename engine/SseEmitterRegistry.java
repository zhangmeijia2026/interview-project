package com.group5.interview.module.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话级 SSE 通道：一个面试 id 一个客户端订阅（MVP 口径）。
 * 无订阅者时 push 为空操作，接口行为不受影响（可用普通 HTTP 响应做兜底）。
 */
@Slf4j
@Component
public class SseEmitterRegistry {
    private static final AtomicLong SEQ = new AtomicLong(0);
    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long interviewId) {
        SseEmitter emitter = new SseEmitter(0L); // 不超时
        emitters.put(interviewId, emitter);
        emitter.onCompletion(() -> emitters.remove(interviewId, emitter));
        emitter.onTimeout(() -> emitters.remove(interviewId, emitter));
        emitter.onError(t -> emitters.remove(interviewId, emitter));
        return emitter;
    }

    public void push(Long interviewId, SseEvent event) {
        SseEmitter emitter = emitters.get(interviewId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().id(String.valueOf(SEQ.incrementAndGet()))
                    .name(event.type()).data(event.data()));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(interviewId, emitter);
        }
    }

    public void remove(Long interviewId) {
        SseEmitter emitter = emitters.remove(interviewId);
        if (emitter != null) emitter.complete();
    }

    /** 心跳：15s 一次，防止代理断开空闲连接。 */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        for (Long id : emitters.keySet()) {
            push(id, SseEvent.of("ping", "{}"));
        }
    }
}
