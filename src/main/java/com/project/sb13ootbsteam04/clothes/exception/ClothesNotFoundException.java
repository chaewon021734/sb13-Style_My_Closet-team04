package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;

/** 수정 대상 의상의 ID를 오류 응답에 남긴다. */
public final class ClothesNotFoundException extends ClothesException {

  public ClothesNotFoundException(UUID clothesId) {
    super(ErrorCode.CLOTHES_NOT_FOUND, Map.of("clothesId", clothesId.toString()));
  }
}
