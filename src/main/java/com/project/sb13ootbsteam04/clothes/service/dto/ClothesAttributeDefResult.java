package com.project.sb13ootbsteam04.clothes.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClothesAttributeDefResult(
    UUID id,
    String name,
    List<String> selectableValues,
    Instant createdAt
) {
}
