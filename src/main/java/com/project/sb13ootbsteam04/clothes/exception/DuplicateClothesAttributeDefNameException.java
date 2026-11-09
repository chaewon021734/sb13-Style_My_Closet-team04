package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Map;

public final class DuplicateClothesAttributeDefNameException extends ClothesException {

  public DuplicateClothesAttributeDefNameException(String name) {
    super(ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NAME_DUPLICATED, Map.of("name", name));
  }

  public DuplicateClothesAttributeDefNameException(String name, Throwable cause) {
    super(ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NAME_DUPLICATED, Map.of("name", name), cause);
  }
}
