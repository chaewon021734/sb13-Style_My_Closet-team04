package com.project.sb13ootbsteam04.clothes.repository.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import java.time.Instant;
import java.util.UUID;

public record ClothesSearchCondition(
    Instant cursor,
    UUID idAfter,
    int limit,
    ClothesType typeEqual,
    UUID ownerId
) {

}
