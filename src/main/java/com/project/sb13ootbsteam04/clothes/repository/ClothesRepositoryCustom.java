package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchCondition;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchResult;

public interface ClothesRepositoryCustom {

  ClothesSearchResult search(ClothesSearchCondition condition);
}
