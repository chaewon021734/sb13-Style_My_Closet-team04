package com.project.sb13ootbsteam04.clothes.service.impl;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeDefNameException;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClothesAttributeDefServiceImpl implements ClothesAttributeDefService {

  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
  private static final String NAME_UNIQUE_CONSTRAINT = "uk_clothes_attribute_defs_name";

  private final ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Override
  @Transactional
  public ClothesAttributeDefResult create(ClothesAttributeDefCreateCommand command) {
    ClothesAttributeDef definition = ClothesAttributeDef.create(command.name(), command.selectableValues());
    String normalizedName = definition.getName();

    try {
      // UNIQUE 제약을 즉시 확인해 추가 조회 없이 동시 등록까지 동일하게 처리한다.
      ClothesAttributeDef saved = clothesAttributeDefRepository.saveAndFlush(definition);
      return new ClothesAttributeDefResult(
          saved.getId(),
          saved.getName(),
          saved.getSelectableValues(),
          saved.getCreatedAt()
      );

    } catch (DataIntegrityViolationException exception) {
      if (isNameUniqueViolation(exception)) {
        throw new DuplicateClothesAttributeDefNameException(normalizedName, exception);
      }
      throw exception;
    }
  }

  /** 이름 UNIQUE 제약 위반만 중복 이름 예외로 변환한다. */
  private boolean isNameUniqueViolation(DataIntegrityViolationException exception) {
    Throwable cause = exception.getMostSpecificCause();
    if (!(cause instanceof SQLException sqlException) || !UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
      return false;
    }

    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof ConstraintViolationException violation) {
        return isNameUniqueConstraint(violation.getConstraintName());
      }
    }
    return false;
  }

  private boolean isNameUniqueConstraint(String constraintName) {
    if (constraintName == null) {
      return false;
    }

    // H2는 스키마·인덱스 정보까지 포함할 수 있어 제약 이름을 토큰 단위로 비교한다.
    return Arrays.stream(constraintName.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
        .anyMatch(NAME_UNIQUE_CONSTRAINT::equals);
  }
}
