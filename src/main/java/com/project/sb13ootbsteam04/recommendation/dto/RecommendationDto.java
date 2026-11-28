package com.project.sb13ootbsteam04.recommendation.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "추천 정보")
public record RecommendationDto(
    @Schema(description = "날씨 ID") UUID weatherId,
    @Schema(description = "사용자 ID") UUID userId,
    @ArraySchema(
        arraySchema = @Schema(description = "의상 정보"),
        schema = @Schema(implementation = OotdDto.class))
    List<OotdDto> clothes
) {

  public RecommendationDto {
    clothes = clothes == null ? List.of() : List.copyOf(clothes);
  }
}
