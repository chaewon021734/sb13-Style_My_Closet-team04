package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeDefRepository extends JpaRepository<ClothesAttributeDef, UUID> {

  // keywordLike 계약에 맞춰 대소문자를 구분하는 부분 일치 검색을 수행한다.
  List<ClothesAttributeDef> findByNameContaining(String keyword, Sort sort);
}
