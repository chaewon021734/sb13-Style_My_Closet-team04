package com.project.sb13ootbsteam04.clothes.event;

import java.util.UUID;

// 의상 속성 정의가 저장된 뒤 전체 사용자 알림에 사용한다.
public record ClothesAttributeDefCreatedEvent(UUID definitionId, String name) {
}
