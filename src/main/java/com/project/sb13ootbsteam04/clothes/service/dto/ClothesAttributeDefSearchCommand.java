package com.project.sb13ootbsteam04.clothes.service.dto;

import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefSortBy;
import org.springframework.data.domain.Sort;

public record ClothesAttributeDefSearchCommand(
    ClothesAttributeDefSortBy sortBy,
    Sort.Direction sortDirection,
    String keywordLike
) {

  public ClothesAttributeDefSearchCommand {
    if (sortBy == null) {
      throw new IllegalArgumentException("정렬 기준은 필수입니다");
    }
    if (sortDirection == null) {
      throw new IllegalArgumentException("정렬 방향은 필수입니다");
    }
  }

  // API 문자열을 서비스 계층의 허용된 정렬 값으로 안전하게 변환한다.
  public ClothesAttributeDefSearchCommand(
      String sortBy,
      Sort.Direction sortDirection,
      String keywordLike
  ) {
    this(ClothesAttributeDefSortBy.from(sortBy), sortDirection, keywordLike);
  }
}
