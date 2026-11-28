package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothesRepository extends JpaRepository<Clothes, UUID>, ClothesRepositoryCustom {

  @EntityGraph(attributePaths = {"attributes", "attributes.definition"})
  @Query("""
      select distinct clothes
      from Clothes clothes
      where clothes.owner.id = :ownerId
      order by clothes.createdAt desc, clothes.id desc
      """)
  List<Clothes> findAllWithAttributesByOwnerId(@Param("ownerId") UUID ownerId);

  // 같은 의상의 수정과 삭제가 같은 행 잠금을 사용한다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Clothes c where c.id = :id")
  Optional<Clothes> findByIdForUpdate(UUID id);
}
