package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ClothesRepository extends JpaRepository<Clothes, UUID>, ClothesRepositoryCustom {

  // 같은 의상의 수정과 삭제가 같은 행 잠금을 사용한다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Clothes c where c.id = :id")
  Optional<Clothes> findByIdForUpdate(UUID id);
}
