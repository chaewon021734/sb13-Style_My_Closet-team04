package com.project.sb13ootbsteam04.clothes.storage;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;

public interface ClothesImageStorage extends AutoCloseable {

  StoredImage upload(ClothesImageCommand image);

  void delete(String objectKey);

  @Override
  default void close() {
  }
}
