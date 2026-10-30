package com.project.sb13ootbsteam04.clothes.controller.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record ClothesAttributeWithDefDto(
    @Schema(description = "속성 정의 ID")
    UUID definitionId,

    @Schema(description = "속성 정의 이름", maxLength = 255)
    String definitionName,

    @ArraySchema(
        arraySchema = @Schema(description = "선택 가능한 값 목록"),
        schema = @Schema(description = "선택 가능한 값", maxLength = 100))
    List<String> selectableValues,

    @Schema(description = "속성 값", maxLength = 100)
    String value
) {

  public ClothesAttributeWithDefDto {
    selectableValues = selectableValues == null ? List.of() : List.copyOf(selectableValues);
  }
}
