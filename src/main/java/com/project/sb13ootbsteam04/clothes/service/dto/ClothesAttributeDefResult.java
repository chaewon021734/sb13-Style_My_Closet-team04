package com.project.sb13ootbsteam04.clothes.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClothesAttributeDefResult(
    UUID id,
    String name,
    List<String> selectableValues,
    Instant createdAt
) {

  public ClothesAttributeDefResult {
    id = Objects.requireNonNull(id, "속성 정의 ID는 필수입니다");
    name = Objects.requireNonNull(name, "속성 정의 이름은 필수입니다");
    selectableValues = List.copyOf(Objects.requireNonNull(selectableValues, "선택 가능한 값 목록은 필수입니다"));
    createdAt = Objects.requireNonNull(createdAt, "속성 정의 생성 시각은 필수입니다");
  }
}
