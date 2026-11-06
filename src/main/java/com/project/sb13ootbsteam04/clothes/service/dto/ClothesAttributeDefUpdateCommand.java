package com.project.sb13ootbsteam04.clothes.service.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 수정 대상 속성 정의 ID와 변경할 전체 속성 정의 값을 전달한다. */
public record ClothesAttributeDefUpdateCommand(
    UUID definitionId,
    String name,
    List<String> selectableValues
) {

  public ClothesAttributeDefUpdateCommand {
    Objects.requireNonNull(definitionId, "의상 속성 정의 ID는 필수입니다");
    selectableValues = List.copyOf(Objects.requireNonNull(selectableValues, "선택 가능한 값 목록은 필수입니다"));
  }
}
