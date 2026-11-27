package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ClothesAttributeRepository extends JpaRepository<ClothesAttribute, UUID> {

  // 엔티티를 하나씩 삭제하지 않고 정의 ID 기준으로 한 번에 삭제한다.
  @Modifying
  @Query("""
      delete from ClothesAttribute attribute
      where attribute.definition.id = :definitionId
      """)
  int deleteAllByDefinitionId(UUID definitionId);
}
