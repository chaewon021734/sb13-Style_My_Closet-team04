package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;

public final class ClothesOwnerNotFoundException extends ClothesException {

  public ClothesOwnerNotFoundException(UUID ownerId) {
    super(ErrorCode.CLOTHES_OWNER_NOT_FOUND, Map.of("ownerId", ownerId.toString()));
  }
}
