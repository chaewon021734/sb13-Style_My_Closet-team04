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
    selectableValues = List.copyOf(Objects.requireNonNull(selectableValues, "선택 가능한 값 목록은 필수입니다"));
  }
}
