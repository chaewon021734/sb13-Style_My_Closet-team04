package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Map;

/** 의상 관리 요청의 인증 및 소유권 오류를 공통 오류 응답으로 전달한다. */
public final class ClothesAccessException extends ClothesException {

  private ClothesAccessException(ErrorCode errorCode) {
    super(errorCode, Map.of());
  }

  public static ClothesAccessException unauthenticated() {
    return new ClothesAccessException(ErrorCode.CLOTHES_AUTHENTICATION_REQUIRED);
  }

  public static ClothesAccessException forbidden() {
    return new ClothesAccessException(ErrorCode.CLOTHES_OWNER_REQUIRED);
  }
}
