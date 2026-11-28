package com.project.sb13ootbsteam04.clothes.service.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import java.util.List;
import java.util.UUID;

public record ClothesResult(
    UUID id,
    UUID ownerId,
    String name,
    String imageUrl,
    ClothesType type,
    List<ClothesAttributeResult> attributes
) {

  public ClothesResult {
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
  }
}
