package com.project.sb13ootbsteam04.clothes.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record ClothesDtoCursorResponse(
    List<ClothesDto> data,
    @Schema(description = "다음 커서")
    String nextCursor,
    @Schema(description = "다음 요청의 보조 커서")
    UUID nextIdAfter,
    @Schema(description = "다음 데이터가 있는지 여부")
    boolean hasNext,
    @Schema(description = "총 데이터 개수")
    long totalCount,
    @Schema(description = "정렬 기준")
    String sortBy,
    @Schema(description = "정렬 방향", allowableValues = {"ASCENDING", "DESCENDING"})
    String sortDirection
) {

  public ClothesDtoCursorResponse {
    // API 응답의 데이터 목록을 null 없이 불변 상태로 유지한다.
    data = data == null ? List.of() : List.copyOf(data);
  }
}
