package com.project.sb13ootbsteam04.notification.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// 이 인스턴스에 연결된 사용자별 SSE 연결
@Component
public class SseEmitterRegistry {

  private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  public void add(UUID userId, SseEmitter emitter) {
    emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);

    // 연결이 끝나면 목록에서 제거
    emitter.onCompletion(() -> remove(userId, emitter));
    emitter.onTimeout(() -> remove(userId, emitter));
    emitter.onError(error -> remove(userId, emitter));
  }

  public List<SseEmitter> emittersOf(UUID userId) {
    return emitters.getOrDefault(userId, List.of());
  }

  public Map<UUID, List<SseEmitter>> all() {
    return emitters;
  }

  public void remove(UUID userId, SseEmitter emitter) {
    emitters.computeIfPresent(userId, (key, list) -> {
      list.remove(emitter);
      return list.isEmpty() ? null : list;
    });
  }
}
