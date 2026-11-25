package com.project.sb13ootbsteam04.clothes.service.dto;

import java.util.List;
import java.util.UUID;

public record ClothesCursorResult(
    List<ClothesResult> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
) {

  public ClothesCursorResult {
    // 서비스 조회 결과가 외부에서 변경되지 않도록 방어적으로 복사한다.
    data = data == null ? List.of() : List.copyOf(data);
  }
}
