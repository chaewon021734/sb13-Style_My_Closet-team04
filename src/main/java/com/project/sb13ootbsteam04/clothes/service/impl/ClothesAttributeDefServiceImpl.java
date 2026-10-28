package com.project.sb13ootbsteam04.clothes.service.impl;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeDefNameException;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClothesAttributeDefServiceImpl implements ClothesAttributeDefService {

  private final ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Override
  @Transactional
  public ClothesAttributeDefResult create(ClothesAttributeDefCreateCommand command) {
    ClothesAttributeDef definition = ClothesAttributeDef.create(command.name(), command.selectableValues());
    String normalizedName = definition.getName();

    if (clothesAttributeDefRepository.existsByName(normalizedName)) {
      throw new DuplicateClothesAttributeDefNameException(normalizedName);
    }

    try {
      ClothesAttributeDef saved = clothesAttributeDefRepository.saveAndFlush(definition);
      // 서비스 내부 UNIQUE 계약 위반을 400으로 변환할 수 있도록 saveAndFlush() 사용

      return new ClothesAttributeDefResult(saved.getId(), saved.getName(), saved.getSelectableValues(), saved.getCreatedAt());

    } catch (DataIntegrityViolationException exception) {
      if (isUniqueViolation(exception)) {
        throw new DuplicateClothesAttributeDefNameException(normalizedName, exception);
      }
      throw exception;
    }
  }

  /** PostgreSQL과 H2의 UNIQUE 위반 SQLSTATE만 중복 이름 예외로 변환한다. */
  private boolean isUniqueViolation(DataIntegrityViolationException exception) {
    Throwable cause = exception.getMostSpecificCause();
    return cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState());
  }
}
