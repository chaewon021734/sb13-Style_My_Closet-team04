package com.project.sb13ootbsteam04.clothes.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

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
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
