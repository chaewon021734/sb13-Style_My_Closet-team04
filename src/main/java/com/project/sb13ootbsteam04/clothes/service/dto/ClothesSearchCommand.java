package com.project.sb13ootbsteam04.clothes.service.dto;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import java.util.UUID;

public record ClothesSearchCommand(
    String cursor,
    UUID idAfter,
    int limit,
    ClothesType typeEqual,
    UUID ownerId
) {

}
