package com.project.sb13ootbsteam04.clothes.service.dto;

import java.util.UUID;
import java.util.Objects;

public record ClothesAttributeCommand(
    UUID definitionId,
    String value
) {

  public ClothesAttributeCommand {
    Objects.requireNonNull(definitionId, "속성 정의 ID는 필수입니다");
    value = Objects.requireNonNull(value, "속성 값은 필수입니다").trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("속성 값은 비어 있을 수 없습니다");
    }
  }
}
