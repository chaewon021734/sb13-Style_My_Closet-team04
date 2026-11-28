package com.project.sb13ootbsteam04.clothes.service.dto;

import java.util.List;
import java.util.UUID;

public record ClothesAttributeResult(
    UUID definitionId,
    String definitionName,
    List<String> selectableValues,
    String value
) {

  public ClothesAttributeResult {
    selectableValues = selectableValues == null ? List.of() : List.copyOf(selectableValues);
  }
}
