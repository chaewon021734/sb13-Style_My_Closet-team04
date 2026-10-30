package com.project.sb13ootbsteam04.clothes.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ClothesAttributeDefinitionNotFoundException extends ClothesException {

  public ClothesAttributeDefinitionNotFoundException(Collection<UUID> definitionIds) {
    super(
        ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND,
        Map.of(
            "definitionIds",
            definitionIds.stream().map(UUID::toString).collect(Collectors.joining(","))
        )
    );
  }
}
