package com.project.sb13ootbsteam04.clothes.service.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClothesCreateCommand(
    UUID ownerId,
    String name,
    ClothesType type,
    List<ClothesAttributeCommand> attributes,
    ClothesImageCommand image
) {

  public ClothesCreateCommand {
    Objects.requireNonNull(ownerId, "의상 소유자는 필수입니다");
    name = Objects.requireNonNull(name, "의상 이름은 필수입니다");
    Objects.requireNonNull(type, "의상 타입은 필수입니다");
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
  }

}
