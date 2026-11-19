package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchCondition;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchResult;

public class ClothesRepositoryCustomImpl implements ClothesRepositoryCustom {

  @Override
  public ClothesSearchResult search(ClothesSearchCondition condition) {
    // Spring Data가 search를 파생 쿼리로 해석하지 않도록 커스텀 저장소 경계를 명시한다.
    throw new UnsupportedOperationException("옷 목록 조회 저장소 기능은 아직 구현되지 않았습니다.");
  }
}
