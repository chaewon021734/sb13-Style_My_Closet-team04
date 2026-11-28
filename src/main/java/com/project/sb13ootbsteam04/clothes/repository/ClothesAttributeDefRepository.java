package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ClothesAttributeDefRepository extends JpaRepository<ClothesAttributeDef, UUID> {

  // keywordLike 계약에 맞춰 대소문자를 구분하는 부분 일치 검색을 수행한다.
  List<ClothesAttributeDef> findByNameContaining(String keyword, Sort sort);

  // 삭제 대상 행을 잠금해 PostgreSQL FK 검사와 삭제 경합을 직렬화한다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select definition
      from ClothesAttributeDef definition
      where definition.id = :definitionId
      """)
  Optional<ClothesAttributeDef> findByIdForUpdate(UUID definitionId);
}
