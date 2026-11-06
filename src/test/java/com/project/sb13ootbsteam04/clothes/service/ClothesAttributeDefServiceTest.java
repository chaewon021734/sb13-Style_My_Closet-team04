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
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefSearchCommand;
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
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
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
  @DisplayName("PostgreSQL 메시지 언어와 무관하게 구조화된 이름 UNIQUE 제약을 판별한다")
  void PostgreSQL_구조화_제약_이름_판별() {
    // given
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(
        "색상",
        List.of("블랙", "크림")
    );
    DataIntegrityViolationException exception = postgresUniqueViolation("uk_clothes_attribute_defs_name");

    given(clothesAttributeDefRepository.saveAndFlush(any(ClothesAttributeDef.class))).willThrow(exception);

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.create(command))
        .isInstanceOf(DuplicateClothesAttributeDefNameException.class);
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

  @Test
  @DisplayName("생성일 기준 내림차순 정렬로 의상 속성 정의 목록을 조회한다")
  void 생성일_내림차순_기준_의상_속성_정의_목록_조회() {
    // given
    ClothesAttributeDef older = definition(UUID.randomUUID(), "색상", Instant.parse("2026-11-04T00:00:00Z"));
    ClothesAttributeDef newer = definition(UUID.randomUUID(), "사이즈", Instant.parse("2026-11-04T00:03:00Z"));
    ClothesAttributeDefSearchCommand command = new ClothesAttributeDefSearchCommand("createdAt", Direction.DESC, null);
    Sort sort = Sort.by(Direction.DESC, "createdAt");
    given(clothesAttributeDefRepository.findAll(sort)).willReturn(List.of(newer, older));

    // when
    List<ClothesAttributeDefResult> result = clothesAttributeDefService.findAll(command);

    // then
    then(clothesAttributeDefRepository).should().findAll(sort);
    assertThat(result)
        .extracting(ClothesAttributeDefResult::name)
        .containsExactly("사이즈", "색상");
  }

  @Test
  @DisplayName("생성일 기준 오름차순 정렬로 의상 속성 정의 목록을 조회한다")
  void 생성일_오름차순_기준_의상_속성_정의_목록_조회() {
    // given
    ClothesAttributeDef older = definition(UUID.randomUUID(), "색상", Instant.parse("2026-11-04T00:00:00Z"));
    ClothesAttributeDef newer = definition(UUID.randomUUID(), "사이즈", Instant.parse("2026-11-04T00:03:00Z"));
    ClothesAttributeDefSearchCommand command = new ClothesAttributeDefSearchCommand("createdAt", Direction.ASC, null);
    Sort sort = Sort.by(Direction.ASC, "createdAt");
    given(clothesAttributeDefRepository.findAll(sort)).willReturn(List.of(older, newer));

    // when
    List<ClothesAttributeDefResult> result = clothesAttributeDefService.findAll(command);

    // then
    then(clothesAttributeDefRepository).should().findAll(sort);
    assertThat(result)
        .extracting(ClothesAttributeDefResult::name)
        .containsExactly("색상", "사이즈");
  }

  @Test
  @DisplayName("속성 정의 이름 기준 내림차순 정렬로 의상 속성 정의 목록을 조회한다")
  void 속성_정의_이름_내림차순_기준_의상_속성_정의_목록_조회() {
    // given
    Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
    ClothesAttributeDef colorDefinition = definition(UUID.randomUUID(), "Color", createdAt);
    ClothesAttributeDef sizeDefinition = definition(UUID.randomUUID(), "Size", createdAt);
    ClothesAttributeDefSearchCommand command = new ClothesAttributeDefSearchCommand("name", Direction.DESC, null);
    Sort sort = Sort.by(Direction.DESC, "name");
    given(clothesAttributeDefRepository.findAll(sort)).willReturn(List.of(sizeDefinition, colorDefinition));

    // when
    List<ClothesAttributeDefResult> result = clothesAttributeDefService.findAll(command);

    // then
    then(clothesAttributeDefRepository).should().findAll(sort);
    assertThat(result)
        .extracting(ClothesAttributeDefResult::name)
        .containsExactly("Size", "Color");
  }

  @Test
  @DisplayName("속성 정의 이름 기준 오름차순 정렬로 의상 속성 정의 목록을 조회한다")
  void 속성_정의_이름_오름차순_기준_의상_속성_정의_목록_조회() {
    // given
    Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
    ClothesAttributeDef colorDefinition = definition(UUID.randomUUID(), "Color", createdAt);
    ClothesAttributeDef sizeDefinition = definition(UUID.randomUUID(), "Size", createdAt);
    ClothesAttributeDefSearchCommand command = new ClothesAttributeDefSearchCommand("name", Direction.ASC, null);
    Sort sort = Sort.by(Direction.ASC, "name");
    given(clothesAttributeDefRepository.findAll(sort)).willReturn(List.of(colorDefinition, sizeDefinition));

    // when
    List<ClothesAttributeDefResult> result = clothesAttributeDefService.findAll(command);

    // then
    then(clothesAttributeDefRepository).should().findAll(sort);
    assertThat(result)
        .extracting(ClothesAttributeDefResult::name)
        .containsExactly("Color", "Size");
  }

  @Test
  @DisplayName("검색어의 앞뒤 공백을 제거하고 이름 부분 일치로 속성 정의를 조회한다")
  void 속성_정의_이름_부분_일치_검색() {
    // given
    ClothesAttributeDef definition = definition(UUID.randomUUID(), "BackgroundColor", Instant.parse("2026-11-04T00:00:00Z"));
    ClothesAttributeDefSearchCommand command = new ClothesAttributeDefSearchCommand("name", Direction.ASC, " Color ");
    Sort sort = Sort.by(Direction.ASC, "name");
    given(clothesAttributeDefRepository.findByNameContaining("Color", sort)).willReturn(List.of(definition));

    // when
    List<ClothesAttributeDefResult> result = clothesAttributeDefService.findAll(command);

    // then
    then(clothesAttributeDefRepository).should().findByNameContaining("Color", sort);
    assertThat(result)
        .extracting(ClothesAttributeDefResult::name)
        .containsExactly("BackgroundColor");
  }

  // 공백뿐인 검색어 분기에 대한 테스트 추가
  @Test
  @DisplayName("공백뿐인 검색어는 이름 필터 없이 전체 속성 정의를 조회한다")
  void 속성_정의_공백_검색어_전체_조회() {
    // given
    ClothesAttributeDef definition = definition(UUID.randomUUID(), "Color", Instant.parse("2026-11-04T00:00:00Z"));
    ClothesAttributeDefSearchCommand command = new ClothesAttributeDefSearchCommand("name", Direction.ASC, "   ");
    Sort sort = Sort.by(Direction.ASC, "name");
    given(clothesAttributeDefRepository.findAll(sort)).willReturn(List.of(definition));

    // when
    List<ClothesAttributeDefResult> result = clothesAttributeDefService.findAll(command);

    // then
    then(clothesAttributeDefRepository).should().findAll(sort);
    then(clothesAttributeDefRepository).shouldHaveNoMoreInteractions();
    assertThat(result).extracting(ClothesAttributeDefResult::name).containsExactly("Color");

  }

  @Test
  @DisplayName("조회 조건이 없으면 속성 정의 목록을 조회하지 않는다")
  void 의상_속성_정의_조회_조건_누락() {
    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.findAll(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("의상 속성 정의 조회 조건은 필수입니다");
    then(clothesAttributeDefRepository).shouldHaveNoInteractions();
  }

  private ClothesAttributeDef definition(UUID id, String name, Instant createdAt) {
    ClothesAttributeDef definition = ClothesAttributeDef.create(name, List.of("옵션"));
    ReflectionTestUtils.setField(definition, "id", id);
    ReflectionTestUtils.setField(definition, "createdAt", createdAt);
    return definition;
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

  private DataIntegrityViolationException postgresUniqueViolation(String constraintName) {
    ServerErrorMessage serverErrorMessage = new ServerErrorMessage(
        "S오류\0C23505\0M중복 키 값이 고유성 제약 조건을 위반했습니다\0n"
            + constraintName + "\0\0"
    );
    PSQLException sqlException = new PSQLException(serverErrorMessage);
    ConstraintViolationException constraintViolation = new ConstraintViolationException(
        "unique constraint violation",
        sqlException,
        null
    );
    return new DataIntegrityViolationException("data integrity violation", constraintViolation);
  }
}
