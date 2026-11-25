package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ClothesRepository extends JpaRepository<Clothes, UUID>, ClothesRepositoryCustom {

  // 한 의상의 기본 정보와 속성을 함께 변경하는 동안 같은 의상의 수정을 직렬화한다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Clothes c where c.id = :id")
  Optional<Clothes> findByIdForUpdate(UUID id);
}
