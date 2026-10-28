package com.project.sb13ootbsteam04.clothes.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClothesAttributeDefDto(
    @Schema(description = "속성 정의 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,

    @Schema(description = "속성 정의 이름", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,

    @Schema(description = "선택 가능한 값 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> selectableValues,

    @Schema(description = "속성 정의 생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    Instant createdAt
) {
}
