package com.project.sb13ootbsteam04.clothes.service.impl;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.project.sb13ootbsteam04.clothes.exception.ClothesNotFoundException;
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
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesUpdateCommand;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    Clothes clothes = Clothes.create(owner, command.name(), command.type(), null);
    applyAttributes(clothes, command.attributes());

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

  @Transactional
  @Override
  public ClothesResult update(ClothesUpdateCommand command) {
    Clothes clothes = clothesRepository.findByIdForUpdate(command.clothesId())
        .orElseThrow(() -> new ClothesNotFoundException(command.clothesId()));

    clothes.update(command.name(), command.type());
    applyAttributes(clothes, command.attributes());
    if (command.image() != null) {
      StoredImage uploaded = clothesImageStorage.upload(command.image());
      deleteOnRollback(command.clothesId(), uploaded.objectKey());
      clothes.updateImageUrl(uploaded.url());
    }

    return clothesMapper.toResult(clothes);
  }

  private void applyAttributes(Clothes clothes, List<ClothesAttributeCommand> requested) {
    // 등록 시 선택값이 없거나 수정 시 속성이 생략되면 조회와 쓰기가 모두 필요 없다.
    if (requested == null || requested.isEmpty()) {
      return;
    }

    Map<UUID, ClothesAttributeDef> definitions = loadDefinitions(requested);
    // 새 옷은 기존 속성이 없어 반복문을 건너뛰고, 수정 시에는 정의 ID로 기존 행을 찾는다.
    Map<UUID, ClothesAttribute> existing = new LinkedHashMap<>();
    for (ClothesAttribute attribute : clothes.getAttributes()) {
      existing.put(attribute.getDefinition().getId(), attribute);
    }

    for (ClothesAttributeCommand item : requested) {
      ClothesAttribute attribute = existing.get(item.definitionId());
      try {
        if (attribute == null) {
          // 이 옷에서 처음 선택한 정의이므로 옷과 정의를 연결하는 속성 행을 만든다.
          ClothesAttribute.create(clothes, definitions.get(item.definitionId()), item.value());
        } else if (!attribute.getValue().equals(item.value())) {
          attribute.changeValue(item.value());
        }
      } catch (IllegalArgumentException exception) {
        throw new InvalidClothesAttributeValueException(item.definitionId(), item.value(), exception);
      }
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

  private void deleteOnRollback(UUID clothesId, String objectKey) {
    // 실제 트랜잭션이 끝날 때까지 파일 본문 대신 이번 수정 업로드의 키만 보관한다.
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {
          deleteImageSafely(objectKey);
        } else if (status == STATUS_UNKNOWN) {
          // DB에 새 URL이 커밋됐을 수 있으므로 이미지는 보존하고 확인할 키를 경고 로그로 기록한다.
          log.warn("의상 수정 커밋 결과 확인 필요. clothesId={}, objectKey={}", clothesId, objectKey);
        }
      }
    });
  }

  private void deleteImageSafely(String objectKey) {
    try {
      clothesImageStorage.delete(objectKey);
    } catch (RuntimeException cleanupFailure) {
      log.error("롤백된 의상 이미지 정리에 실패했습니다. objectKey={}", objectKey, cleanupFailure);
    }
  }
}
