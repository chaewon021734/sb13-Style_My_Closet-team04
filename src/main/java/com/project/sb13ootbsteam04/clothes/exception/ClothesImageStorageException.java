package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Map;

public final class ClothesImageStorageException extends ClothesException {

  private ClothesImageStorageException(ErrorCode errorCode, String operation, Throwable cause) {
    super(errorCode, Map.of("image", "이미지 저장소 처리에 실패했습니다", "operation", operation), cause);
  }

  public static ClothesImageStorageException notConfigured(Throwable cause) {
    return new ClothesImageStorageException(ErrorCode.CLOTHES_IMAGE_STORAGE_NOT_CONFIGURED, "configuration", cause);
  }

  public static ClothesImageStorageException uploadFailed(Throwable cause) {
    return new ClothesImageStorageException(ErrorCode.CLOTHES_IMAGE_UPLOAD_FAILED, "upload", cause);
  }

  public static ClothesImageStorageException deleteFailed(Throwable cause) {
    return new ClothesImageStorageException(ErrorCode.CLOTHES_IMAGE_DELETE_FAILED, "delete", cause);
  }
}
