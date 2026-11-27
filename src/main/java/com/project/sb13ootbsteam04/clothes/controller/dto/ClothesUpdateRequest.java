package com.project.sb13ootbsteam04.clothes.controller.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClothesUpdateRequest(
    @Schema(description = "의상 이름")
    @Size(max = 255, message = "의상 이름은 255자까지만 입력 가능합니다.")
    String name,

    @Schema(description = "의상 타입")
    ClothesType type,

    @Schema(description = "의상 속성")
    List<@NotNull @Valid ClothesAttributeDto> attributes
) {

}
