package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;

public final class DuplicateClothesAttributeException extends ClothesException {

  public DuplicateClothesAttributeException(UUID definitionId) {
    super(ErrorCode.CLOTHES_ATTRIBUTE_DUPLICATED, Map.of("definitionId", definitionId.toString()));
  }
}
