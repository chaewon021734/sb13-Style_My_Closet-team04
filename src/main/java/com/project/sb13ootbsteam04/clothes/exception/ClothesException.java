package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.global.exception.OtbooException;
import java.util.Map;

public abstract class ClothesException extends OtbooException {

  protected ClothesException(ErrorCode errorCode, Map<String, String> details) {
    super(errorCode, details);
  }

  protected ClothesException(ErrorCode errorCode, Map<String, String> details, Throwable cause) {
    super(errorCode, details, cause);
  }
}
