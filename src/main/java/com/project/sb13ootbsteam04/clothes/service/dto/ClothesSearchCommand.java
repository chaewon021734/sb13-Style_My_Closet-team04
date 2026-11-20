package com.project.sb13ootbsteam04.clothes.service.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import java.util.UUID;

public record ClothesSearchCommand(
    String cursor,
    UUID idAfter,
    int limit,
    ClothesType typeEqual,
    UUID ownerId
) {

  public static final int MAX_LIMIT = 100;

  public ClothesSearchCommand {
    // 외부 API를 거치지 않는 서비스 호출에도 동일한 조회 범위를 적용한다.
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit은 1 이상 100 이하여야 합니다.");
    }
    if (ownerId == null) {
      throw new IllegalArgumentException("소유자 ID는 필수입니다.");
    }
    if ((cursor == null) != (idAfter == null)) {
      throw new IllegalArgumentException("cursor와 idAfter는 함께 제공되어야 합니다.");
    }
  }
}
