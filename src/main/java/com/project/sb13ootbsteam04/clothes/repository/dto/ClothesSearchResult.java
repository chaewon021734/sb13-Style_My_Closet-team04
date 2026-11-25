package com.project.sb13ootbsteam04.clothes.repository.dto;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import java.util.List;

public record ClothesSearchResult(
    List<Clothes> content,
    boolean hasNext,
    long totalCount
) {

  public ClothesSearchResult {
    // 조회 결과 컬렉션의 null 전파와 외부 변경을 방지한다.
    content = content == null ? List.of() : List.copyOf(content);
  }
}
