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
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
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

    return isNameUniqueConstraint(resolveConstraintName(exception));
  }

  private String resolveConstraintName(Throwable exception) {
    String hibernateConstraintName = null;

    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof PSQLException postgresException) { // 구조화된 제약조건명 추출
        ServerErrorMessage serverErrorMessage = postgresException.getServerErrorMessage();
        if (serverErrorMessage != null && serverErrorMessage.getConstraint() != null) {
          // PostgreSQL 프로토콜의 구조화 필드는 서버 메시지 언어에 영향을 받지 않는다.
          return serverErrorMessage.getConstraint();
        }
      }
      if (current instanceof ConstraintViolationException violation && hibernateConstraintName == null) {
        // H2 등 PostgreSQL 이외 테스트 DB에서는 Hibernate가 추출한 이름을 사용한다.
        hibernateConstraintName = violation.getConstraintName();
      }
    }
    return hibernateConstraintName;
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
