package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeDefRepository extends JpaRepository<ClothesAttributeDef, UUID> {

}
