package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeDefNameException;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefUpdateCommand;
import com.project.sb13ootbsteam04.clothes.service.impl.ClothesAttributeDefServiceImpl;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, ClothesAttributeDefServiceImpl.class})
@DisplayName("의상 속성 정의 서비스 영속성 통합")
class ClothesAttributeDefServiceIntegrationTest {

  @Autowired
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Autowired
  private ClothesAttributeDefService clothesAttributeDefService;

  @BeforeEach
  void setUp() {
    // 공통 기존 속성 정의
    clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));
  }

  @Test
  @DisplayName("등록 flush에서 발생한 이름 UNIQUE 위반을 중복 이름 예외로 변환한다")
  void 이름_UNIQUE_위반_예외_변환() {
    // given
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(
        " 색상 ",
        List.of("화이트", "핑크")
    );

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.create(command))
        .isInstanceOf(DuplicateClothesAttributeDefNameException.class)
        .hasMessageContaining("이미 존재하는 의상 속성 정의 이름입니다");
  }

  @Test
  @DisplayName("수정 dirty checking flush에서 발생한 이름 UNIQUE 위반을 중복 이름 예외로 변환한다")
  void 수정_이름_UNIQUE_위반_예외_변환() {
    // given
    ClothesAttributeDef updateTarget = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("사이즈", List.of("S", "M", "L")));
    UUID definitionId = updateTarget.getId();
    ClothesAttributeDefUpdateCommand command = new ClothesAttributeDefUpdateCommand(
        definitionId,
        "색상",
        List.of("화이트", "핑크")
    );

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.update(command))
        .isInstanceOf(DuplicateClothesAttributeDefNameException.class)
        .hasMessageContaining("이미 존재하는 의상 속성 정의 이름입니다");
  }
}
