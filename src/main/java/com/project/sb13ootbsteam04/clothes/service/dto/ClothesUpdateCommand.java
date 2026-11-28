package com.project.sb13ootbsteam04.clothes.service.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClothesUpdateCommand(
    UUID clothesId,
    String name,
    ClothesType type,
    List<ClothesAttributeCommand> attributes,
    ClothesImageCommand image
) {
  public ClothesUpdateCommand {
    Objects.requireNonNull(clothesId, "수정 대상 의상은 필수입니다");
    // PATCH에서 생략한 필드는 기존 값을 유지하도록 null을 보존한다.
    if (name != null) {
      name = name.trim();
      if (name.isEmpty()) {
        throw new IllegalArgumentException("의상 이름은 비어 있을 수 없습니다");
      }
      if (name.length() > 255) {
        throw new IllegalArgumentException("의상 이름은 255자까지만 입력 가능합니다");
      }
    }
    if (attributes != null) {
      attributes = List.copyOf(attributes);
    }
  }
}
