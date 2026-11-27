package com.project.sb13ootbsteam04.clothes.storage;

import com.project.sb13ootbsteam04.clothes.exception.ClothesImageStorageException;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;

public class UnavailableClothesImageStorage implements ClothesImageStorage {

  @Override
  public StoredImage upload(ClothesImageCommand image) {
    throw notConfigured();
  }

  @Override
  public void delete(String objectKey) {
    // 커밋 후 정리 실패를 서비스가 객체 키와 함께 기록할 수 있도록 알린다.
    throw notConfigured();
  }

  private ClothesImageStorageException notConfigured() {
    return ClothesImageStorageException.notConfigured(new IllegalStateException("otboo.storage.s3 설정이 필요합니다"));
  }
}
