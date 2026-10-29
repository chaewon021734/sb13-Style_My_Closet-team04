package com.project.sb13ootbsteam04.clothes.controller.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClothesAttributeDefDto(
    @Schema(description = "속성 정의 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,

    @Schema(description = "속성 정의 이름", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
    String name,

    @ArraySchema(
        arraySchema = @Schema(
            description = "선택 가능한 값 목록",
            requiredMode = Schema.RequiredMode.REQUIRED),
        schema = @Schema(description = "선택 가능한 값", maxLength = 100))
    List<String> selectableValues,

    @Schema(description = "속성 정의 생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    Instant createdAt
) {

  public ClothesAttributeDefDto {
    id = Objects.requireNonNull(id, "속성 정의 ID는 필수입니다");
    name = Objects.requireNonNull(name, "속성 정의 이름은 필수입니다");
    selectableValues = List.copyOf(
        Objects.requireNonNull(selectableValues, "선택 가능한 값 목록은 필수입니다")
    );
    createdAt = Objects.requireNonNull(createdAt, "속성 정의 생성 시각은 필수입니다");
  }
}
