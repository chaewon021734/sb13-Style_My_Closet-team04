package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeDefNameException;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import com.project.sb13ootbsteam04.clothes.service.impl.ClothesAttributeDefServiceImpl;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("의상 속성 정의 서비스")
@ExtendWith(MockitoExtension.class)
class ClothesAttributeDefServiceTest {

  @Mock
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

  @InjectMocks
  private ClothesAttributeDefServiceImpl clothesAttributeDefService;

  @Test
  @DisplayName("속성 정의 이름과 선택 가능한 값으로 의상 속성 정의를 등록한다")
  void 의상_속성_정의_등록_성공() {
    // given
    UUID definitionId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-28T00:00:00Z");
    String name = "색상";
    List<String> selectableValues = List.of("블랙", "크림");
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(name, selectableValues);

    given(clothesAttributeDefRepository.saveAndFlush(any(ClothesAttributeDef.class)))
        .willAnswer(invocation -> {
          ClothesAttributeDef saved = invocation.getArgument(0);
          ReflectionTestUtils.setField(saved, "id", definitionId);
          ReflectionTestUtils.setField(saved, "createdAt", createdAt);
          return saved;
        });

    // when
    ClothesAttributeDefResult result = clothesAttributeDefService.create(command);

    // then
    ArgumentCaptor<ClothesAttributeDef> captor = ArgumentCaptor.forClass(ClothesAttributeDef.class);
    then(clothesAttributeDefRepository).should().saveAndFlush(captor.capture());
    ClothesAttributeDef saved = captor.getValue();

    assertAll(
        () -> assertThat(saved.getName()).isEqualTo(name),
        () -> assertThat(saved.getSelectableValues()).containsExactlyElementsOf(selectableValues),
        () -> assertThat(result.id()).isEqualTo(definitionId),
        () -> assertThat(result.name()).isEqualTo(name),
        () -> assertThat(result.selectableValues()).containsExactlyElementsOf(selectableValues),
        () -> assertThat(result.createdAt()).isEqualTo(createdAt)
    );
  }

  @Test
  @DisplayName("이름 UNIQUE 제약을 위반하면 중복 이름 예외로 변환한다")
  void 의상_속성_정의_이름_중복() {
    // given
    String name = "색상";
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(name, List.of("블랙", "크림"));
    DataIntegrityViolationException uniqueViolation = uniqueViolation("uk_clothes_attribute_defs_name");

    given(clothesAttributeDefRepository.saveAndFlush(any(ClothesAttributeDef.class))).willThrow(uniqueViolation);

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.create(command))
        .isInstanceOf(DuplicateClothesAttributeDefNameException.class)
        .hasMessageContaining("이미 존재하는 의상 속성 정의 이름입니다")
        .satisfies(exception -> assertThat(((DuplicateClothesAttributeDefNameException) exception).getDetails())
            .containsEntry("name", name));
  }

  @Test
  @DisplayName("다른 UNIQUE 제약 위반은 중복 이름 예외로 숨기지 않는다")
  void 의상_속성_정의_다른_UNIQUE_제약_위반() {
    // given
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(
        "색상",
        List.of("블랙", "크림")
    );
    DataIntegrityViolationException exception = uniqueViolation("pk_clothes_attribute_defs");

    given(clothesAttributeDefRepository.saveAndFlush(any(ClothesAttributeDef.class))).willThrow(exception);

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.create(command)).isSameAs(exception);
  }

  @Test
  @DisplayName("제약 이름을 확인할 수 없는 UNIQUE 위반은 중복 이름 예외로 추측하지 않는다")
  void 의상_속성_정의_제약_이름_없는_UNIQUE_위반() {
    // given
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(
        "색상",
        List.of("블랙", "크림")
    );
    DataIntegrityViolationException exception = new DataIntegrityViolationException(
        "unknown unique violation",
        new SQLException("duplicate key", "23505")
    );

    given(clothesAttributeDefRepository.saveAndFlush(any(ClothesAttributeDef.class))).willThrow(exception);

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.create(command)).isSameAs(exception);
  }

  @Test
  @DisplayName("UNIQUE 이외의 무결성 위반은 중복 이름 예외로 숨기지 않는다")
  void 의상_속성_정의_다른_무결성_위반() {
    // given
    String name = "색상";
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(name, List.of("블랙", "크림"));
    SQLException notNullViolation = new SQLException("not-null violation", "23502");
    DataIntegrityViolationException exception = new DataIntegrityViolationException("invalid definition", notNullViolation);

    given(clothesAttributeDefRepository.saveAndFlush(any(ClothesAttributeDef.class)))
        .willThrow(exception);

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.create(command))
        .isSameAs(exception);
  }

  private DataIntegrityViolationException uniqueViolation(String constraintName) {
    SQLException sqlException = new SQLException("duplicate key", "23505");
    ConstraintViolationException constraintViolation = new ConstraintViolationException(
        "unique constraint violation",
        sqlException,
        constraintName
    );
    return new DataIntegrityViolationException("data integrity violation", constraintViolation);
  }
}
