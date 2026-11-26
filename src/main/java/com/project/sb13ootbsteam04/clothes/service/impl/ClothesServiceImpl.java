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
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchCondition;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchResult;
import com.project.sb13ootbsteam04.clothes.service.ClothesService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCursorResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesSearchCommand;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.StoredImage;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Slf4j
@RequiredArgsConstructor
@Service
public class ClothesServiceImpl implements ClothesService {

  // 옷 목록 조회 API에 정렬 요청 파라미터가 없으므로 서비스가 고정된 조회 정책을 반환한다.
  private static final String CLOTHES_SORT_BY = "createdAt";
  private static final String CLOTHES_SORT_DIRECTION = "DESCENDING";

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

  @Override
  @Transactional(readOnly = true)
  public ClothesCursorResult search(ClothesSearchCommand command) {
    ClothesSearchCondition condition = new ClothesSearchCondition(
        parseCursor(command.cursor()),
        command.idAfter(),
        command.limit(),
        command.typeEqual(),
        command.ownerId()
    );
    ClothesSearchResult page = clothesRepository.search(condition);

    List<ClothesResult> data = page.content().stream().map(clothesMapper::toResult).toList();

    // 다음 페이지가 있을 때만 현재 페이지의 마지막 의상을 커서로 노출한다.
    Clothes lastClothes = page.hasNext() && !page.content().isEmpty() ? page.content().get(page.content().size() - 1) : null;
    String nextCursor = lastClothes == null ? null : lastClothes.getCreatedAt().toString();
    UUID nextIdAfter = lastClothes == null ? null : lastClothes.getId();

    return new ClothesCursorResult(
        data,
        nextCursor,
        nextIdAfter,
        page.hasNext(),
        page.totalCount(),
        CLOTHES_SORT_BY,
        CLOTHES_SORT_DIRECTION
    );
  }

  @Override
  public void delete(UUID actorId, UUID clothesId) {
    throw new UnsupportedOperationException("의상 삭제 기능은 아직 구현되지 않았습니다.");
  }

  private Instant parseCursor(String cursor) {
    // 첫 페이지 조회 시에는 커서가 없으므로 null을 반환한다.
    if (cursor == null) {
      return null;
    }

    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("cursor는 ISO-8601 날짜 형식이어야 합니다.", exception);
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
