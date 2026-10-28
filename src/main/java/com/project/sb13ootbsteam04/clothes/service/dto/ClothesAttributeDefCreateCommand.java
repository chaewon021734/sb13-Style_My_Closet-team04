package com.project.sb13ootbsteam04.clothes.service.dto;

import java.util.List;

public record ClothesAttributeDefCreateCommand(
    String name,
    List<String> selectableValues
) {
}
