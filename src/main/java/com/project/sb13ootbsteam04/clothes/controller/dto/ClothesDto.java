package com.project.sb13ootbsteam04.clothes.controller.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record ClothesDto(

    @Schema(description = "의상 ID")
    UUID id,

    @Schema(description = "소유자 ID")
    UUID ownerId,

    @Schema(description = "의상 이름", maxLength = 255)
    String name,

    @Schema(description = "의상 이미지 URL", maxLength = 500, nullable = true)
    String imageUrl,

    @Schema(description = "의상 타입")
    ClothesType type,

    @Schema(description = "의상 속성")
    List<ClothesAttributeWithDefDto> attributes
) {

  public ClothesDto {
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
  }
}
