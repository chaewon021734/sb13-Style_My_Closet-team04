package com.project.sb13ootbsteam04.clothes.controller.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record ClothesCreateRequest(

    @Schema(description = "소유자 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "의상 소유자 ID는 필수입니다")
    UUID ownerId,

    @Schema(description = "의상 이름", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 255, message = "의상 이름은 255자까지만 입력 가능합니다.")
    @NotBlank(message = "의상 이름은 필수입니다")
    String name,

    @Schema(description = "의상 타입", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "의상 타입은 필수입니다")
    ClothesType type,

    @Schema(description = "의상 속성")
    List<@NotNull @Valid ClothesAttributeDto> attributes
) {

  public ClothesCreateRequest {
    attributes = attributes == null
        ? List.of()
        : Collections.unmodifiableList(new ArrayList<>(attributes));
  }
}
