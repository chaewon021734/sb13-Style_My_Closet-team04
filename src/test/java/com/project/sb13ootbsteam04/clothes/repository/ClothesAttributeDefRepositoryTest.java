package com.project.sb13ootbsteam04.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import java.util.List;
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

  @Test
  @DisplayName("같은 이름의 속성 정의가 존재하면 true를 반환한다")
  void 속성_정의_이름_존재_확인() {
    // given
    clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));

    // when
    boolean exists = clothesAttributeDefRepository.existsByName("색상");
    boolean notExists = clothesAttributeDefRepository.existsByName("소재");

    // then
    assertAll(
        () -> assertThat(exists).isTrue(),
        () -> assertThat(notExists).isFalse()
    );
  }

  @Test
  @DisplayName("같은 이름의 속성 정의를 저장하면 UNIQUE 제약을 위반한다")
  void 속성_정의_이름_UNIQUE_제약() {
    // given
    clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefRepository.saveAndFlush(
        ClothesAttributeDef.create("색상", List.of("화이트", "핑크"))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
