package com.project.sb13ootbsteam04.clothes.controller.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClothesAttributeDefCreateRequest(

    @Schema(
        description = "속성 정의 이름",
        example = "색상",
        maxLength = 255,
        requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 255, message = "속성 정의 이름은 255자까지만 입력 가능합니다")
    @NotBlank(message = "속성 정의 이름은 필수입니다")
    String name,

    @ArraySchema(
        arraySchema = @Schema(
            description = "선택 가능한 값 목록",
            requiredMode = Schema.RequiredMode.REQUIRED),
        schema = @Schema(description = "선택 가능한 값", maxLength = 100))
    @NotEmpty(message = "선택 가능한 값은 최소 하나 이상 필요합니다")
    List<
        @NotBlank(message = "선택 가능한 값은 비어 있을 수 없습니다")
        @Size(max = 100, message = "선택 가능한 값은 100자까지만 입력 가능합니다")
        String> selectableValues
) {
}
