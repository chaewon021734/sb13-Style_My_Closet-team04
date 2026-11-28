package com.project.sb13ootbsteam04.clothes.service.dto;

import java.util.List;
import java.util.Objects;

public record ClothesAttributeDefCreateCommand(
    String name,
    List<String> selectableValues
) {

  public ClothesAttributeDefCreateCommand {
    selectableValues = List.copyOf(Objects.requireNonNull(selectableValues, "선택 가능한 값 목록은 필수입니다"));
  }
}
