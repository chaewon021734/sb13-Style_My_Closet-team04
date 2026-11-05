package com.project.sb13ootbsteam04.clothes.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ClothesAttributeDto(

    @Schema(description = "속성 정의 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "속성 정의 ID는 필수입니다")
    UUID definitionId,

    @Schema(description = "속성 값", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "속성 값은 필수입니다")
    @Size(max = 100, message = "속성 값은 100자까지만 입력 가능합니다")
    String value
) {

}
