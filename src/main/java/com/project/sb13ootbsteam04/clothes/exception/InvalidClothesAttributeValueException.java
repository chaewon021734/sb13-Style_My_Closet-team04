package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;

public final class InvalidClothesAttributeValueException extends ClothesException {

  public InvalidClothesAttributeValueException(UUID definitionId, String value, Throwable cause) {
    super(
        ErrorCode.CLOTHES_ATTRIBUTE_VALUE_INVALID,
        Map.of(
            "definitionId", definitionId.toString(),
            "value", value
        ),
        cause
    );
  }
}
