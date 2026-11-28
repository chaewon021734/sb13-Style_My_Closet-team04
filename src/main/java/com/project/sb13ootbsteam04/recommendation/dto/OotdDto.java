package com.project.sb13ootbsteam04.recommendation.dto;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeWithDefDto;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "의상 정보")
public record OotdDto(
    @Schema(description = "의상 ID") UUID clothesId,
    @Schema(description = "의상 이름") String name,
    @Schema(description = "의상 이미지 URL") String imageUrl,
    @Schema(description = "의상 타입") ClothesType type,
    @ArraySchema(
        arraySchema = @Schema(description = "의상 속성"),
        schema = @Schema(implementation = ClothesAttributeWithDefDto.class))
    List<ClothesAttributeWithDefDto> attributes
) {

  public OotdDto {
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
  }
}
