package com.project.sb13ootbsteam04.clothes.storage;

import com.project.sb13ootbsteam04.clothes.exception.ClothesImageStorageException;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;

public class UnavailableClothesImageStorage implements ClothesImageStorage {

  @Override
  public StoredImage upload(ClothesImageCommand image) {
    throw ClothesImageStorageException.notConfigured(new IllegalStateException("otboo.storage.s3 설정이 필요합니다"));
  }

  @Override
  public void delete(String objectKey) {
  }
}
