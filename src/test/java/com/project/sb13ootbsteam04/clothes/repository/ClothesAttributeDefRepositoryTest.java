package com.project.sb13ootbsteam04.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@DisplayName("의상 속성 정의 저장소")
class ClothesAttributeDefRepositoryTest {

  @Autowired
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("속성 정의와 선택 가능한 값을 저장하고 다시 조회한다")
  void 속성_정의_JSONB_저장_조회() {
    // given
    ClothesAttributeDef saved = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));
    UUID definitionId = saved.getId();
    entityManager.clear();

    // when
    ClothesAttributeDef found = clothesAttributeDefRepository.findById(definitionId).orElseThrow();

    // then
    assertAll(
        () -> assertNotNull(found.getId()),
        () -> assertNotNull(found.getCreatedAt()),
        () -> assertEquals("색상", found.getName()),
        () -> assertEquals(List.of("블랙", "크림"), found.getSelectableValues())
    );
  }

  @Test
  @DisplayName("같은 이름의 속성 정의를 저장하면 UNIQUE 제약을 위반한다")
  void 속성_정의_이름_UNIQUE_제약() {
    // given
    clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefRepository.saveAndFlush(
        ClothesAttributeDef.create(" 색상 ", List.of("화이트", "핑크"))))
        // SQLSTATE 23505 검증
        .isInstanceOf(DataIntegrityViolationException.class)
        .satisfies(exception -> assertThat(
            ((DataIntegrityViolationException) exception).getMostSpecificCause())
        .isInstanceOfSatisfying(SQLException.class,
            sqlException -> assertThat(sqlException.getSQLState()).isEqualTo("23505")));
  }

  @Test
  @DisplayName("생성일 기준 오름차순으로 정렬하여 모든 속성 정의를 조회한다")
  void 생성일_기준_오름차순_속성_정의_조회() {
    // given
    ClothesAttributeDef oldest = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));
    ClothesAttributeDef newest = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("사이즈", List.of("S", "M", "L")));
    Instant baseTime = Instant.parse("2026-11-04T00:00:00Z");
    updateClothesAttributeDefCreatedAt(oldest.getId(), baseTime);
    updateClothesAttributeDefCreatedAt(newest.getId(), baseTime.plusSeconds(180));
    entityManager.clear();

    // when
    List<ClothesAttributeDef> result = clothesAttributeDefRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"));

    // then
    assertThat(result).extracting(ClothesAttributeDef::getName).containsExactly("색상", "사이즈");
  }

  @Test
  @DisplayName("생성일 기준 내림차순으로 정렬하여 모든 속성 정의를 조회한다")
  void 생성일_기준_내림차순_속성_정의_조회() {
    // given
    ClothesAttributeDef oldest = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));
    ClothesAttributeDef newest = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("사이즈", List.of("S", "M", "L")));
    Instant baseTime = Instant.parse("2026-11-04T00:00:00Z");
    updateClothesAttributeDefCreatedAt(oldest.getId(), baseTime);
    updateClothesAttributeDefCreatedAt(newest.getId(), baseTime.plusSeconds(180));
    entityManager.clear();

    // when
    List<ClothesAttributeDef> result = clothesAttributeDefRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

    // then
    assertThat(result).extracting(ClothesAttributeDef::getName).containsExactly("사이즈", "색상");
  }

  @Test
  @DisplayName("속성 정의 이름 기준 오름차순으로 정렬하여 모든 속성 정의를 조회한다")
  void 속성_정의_이름_기준_오름차순_속성_정의_조회() {
    // given
    clothesAttributeDefRepository.save(ClothesAttributeDef.create("Size", List.of("S", "M", "L")));
    clothesAttributeDefRepository.save(ClothesAttributeDef.create("Color", List.of("Black", "Cream")));
    clothesAttributeDefRepository.flush();

    // when
    List<ClothesAttributeDef> result = clothesAttributeDefRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));

    // then
    assertThat(result).extracting(ClothesAttributeDef::getName).containsExactly("Color", "Size");
  }

  @Test
  @DisplayName("속성 정의 이름 기준 내림차순으로 정렬하여 모든 속성 정의를 조회한다")
  void 속성_정의_이름_기준_내림차순_속성_정의_조회() {
    // given
    clothesAttributeDefRepository.save(ClothesAttributeDef.create("Color", List.of("Black", "Cream")));
    clothesAttributeDefRepository.save(ClothesAttributeDef.create("Size", List.of("S", "M", "L")));
    clothesAttributeDefRepository.flush();

    // when
    List<ClothesAttributeDef> result = clothesAttributeDefRepository.findAll(Sort.by(Sort.Direction.DESC, "name"));

    // then
    assertThat(result).extracting(ClothesAttributeDef::getName).containsExactly("Size", "Color");
  }

  @Test
  @DisplayName("속성 정의 이름은 대소문자를 구분하여 부분 일치 검색한다")
  void 속성_정의_이름_대소문자_구분_부분_일치_검색() {
    // given
    // 검색어와 일치하는 행을 두 개 이상 저장하여 정렬을 검증한다
    clothesAttributeDefRepository.save(ClothesAttributeDef.create("BackgroundColor", List.of("Black", "Cream")));
    clothesAttributeDefRepository.save(ClothesAttributeDef.create("AccentColor", List.of("Red")));
    clothesAttributeDefRepository.flush();
    Sort sort = Sort.by(Sort.Direction.ASC, "name");

    // when
    List<ClothesAttributeDef> matched = clothesAttributeDefRepository.findByNameContaining("Color", sort);
    List<ClothesAttributeDef> caseMismatch = clothesAttributeDefRepository.findByNameContaining("color", sort);

    // then
    assertAll(
        () -> assertThat(matched).extracting(ClothesAttributeDef::getName).containsExactly("AccentColor", "BackgroundColor"),
        () -> assertThat(caseMismatch).isEmpty()
    );
  }

  private void updateClothesAttributeDefCreatedAt(UUID definitionId, Instant createdAt) {
    entityManager.createNativeQuery("UPDATE clothes_attribute_defs SET created_at = :createdAt WHERE id = :definitionId")
        .setParameter("createdAt", createdAt)
        .setParameter("definitionId", definitionId)
        .executeUpdate();
    entityManager.flush();
  }
}
