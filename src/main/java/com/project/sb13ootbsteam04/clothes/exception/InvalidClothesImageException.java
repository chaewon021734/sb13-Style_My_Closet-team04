package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Map;

public final class InvalidClothesImageException extends ClothesException {

  private InvalidClothesImageException(ErrorCode errorCode, String detail) {
    super(errorCode, Map.of("image", detail));
  }

  private InvalidClothesImageException(ErrorCode errorCode, String detail, Throwable cause) {
    super(errorCode, Map.of("image", detail), cause);
  }

  public static InvalidClothesImageException contentTypeRequired() {
    return new InvalidClothesImageException(ErrorCode.CLOTHES_IMAGE_CONTENT_TYPE_REQUIRED, "콘텐츠 타입을 확인해 주세요");
  }

  public static InvalidClothesImageException unsupportedType() {
    return new InvalidClothesImageException(ErrorCode.CLOTHES_IMAGE_TYPE_UNSUPPORTED, "JPEG, PNG, GIF, WebP 형식만 사용할 수 있습니다");
  }

  public static InvalidClothesImageException empty() {
    return new InvalidClothesImageException(ErrorCode.CLOTHES_IMAGE_EMPTY, "파일 내용이 비어 있습니다");
  }

  public static InvalidClothesImageException tooLarge() {
    return new InvalidClothesImageException(ErrorCode.CLOTHES_IMAGE_TOO_LARGE, "최대 크기는 10MB입니다");
  }

  public static InvalidClothesImageException contentMismatch() {
    return new InvalidClothesImageException(ErrorCode.CLOTHES_IMAGE_CONTENT_MISMATCH, "올바른 이미지 파일인지 확인해 주세요");
  }

  public static InvalidClothesImageException readFailed(Throwable cause) {
    return new InvalidClothesImageException(ErrorCode.CLOTHES_IMAGE_READ_FAILED, "파일 읽기에 실패했습니다", cause);
  }
}
