package com.project.sb13ootbsteam04.clothes.service.impl;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeException;
import com.project.sb13ootbsteam04.clothes.exception.InvalidClothesAttributeValueException;
import com.project.sb13ootbsteam04.clothes.exception.ClothesOwnerNotFoundException;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapper;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.clothes.service.ClothesService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.StoredImage;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Slf4j
@RequiredArgsConstructor
@Service
public class ClothesServiceImpl implements ClothesService {

  private final UserRepository userRepository;
  private final ClothesRepository clothesRepository;
  private final ClothesAttributeDefRepository clothesAttributeDefRepository;
  private final ClothesImageStorage clothesImageStorage;
  private final ClothesMapper clothesMapper;
  private final TransactionOperations transactionOperations;

  @Override
  public ClothesResult create(ClothesCreateCommand command) {
    User owner = findOwner(command.ownerId());
    Map<UUID, ClothesAttributeDef> definitions = loadDefinitions(command.attributes());

    Clothes clothes = Clothes.create(owner, command.name(), command.type(), null);

    for (ClothesAttributeCommand attribute : command.attributes()) {
      try {
        ClothesAttribute.create(clothes, definitions.get(attribute.definitionId()), attribute.value());
      } catch (IllegalArgumentException exception) {
        throw new InvalidClothesAttributeValueException(
            attribute.definitionId(),
            attribute.value(),
            exception
        );
      }
    }

    StoredImage storedImage = null;

    try {
      if (command.image() != null) {
        storedImage = clothesImageStorage.upload(command.image());
        clothes.updateImageUrl(storedImage.url());
      }

      return transactionOperations.execute(status -> {
        Clothes saved = clothesRepository.save(clothes);
        return clothesMapper.toResult(saved);
      });

    } catch (RuntimeException exception) {
      if (storedImage != null) {
        deleteImageSafely(storedImage.objectKey());
      }
      throw exception;
    }
  }

  private User findOwner(UUID ownerId) {
    return userRepository.findById(ownerId)
        .orElseThrow(() -> new ClothesOwnerNotFoundException(ownerId));
  }

  private Map<UUID, ClothesAttributeDef> loadDefinitions(List<ClothesAttributeCommand> attributes) {

    Set<UUID> definitionIds = new LinkedHashSet<>();

    for (ClothesAttributeCommand attribute : attributes) {
      if (!definitionIds.add(attribute.definitionId())) {
        throw new DuplicateClothesAttributeException(attribute.definitionId());
      }
    }

    if (definitionIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, ClothesAttributeDef> definitions = new LinkedHashMap<>();
    clothesAttributeDefRepository.findAllById(definitionIds)
        .forEach(definition -> definitions.put(definition.getId(), definition));

    List<UUID> missingIds = definitionIds.stream()
        .filter(id -> !definitions.containsKey(id))
        .toList();

    if (!missingIds.isEmpty()) {
      throw new ClothesAttributeDefinitionNotFoundException(missingIds);
    }

    return definitions;
  }

  private void deleteImageSafely(String objectKey) {
    try {
      clothesImageStorage.delete(objectKey);
    } catch (RuntimeException cleanupFailure) {
      log.error("롤백된 의상 이미지 정리에 실패했습니다. objectKey={}", objectKey, cleanupFailure);
    }
  }
}
