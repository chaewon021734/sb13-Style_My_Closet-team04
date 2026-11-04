package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeDefNameException;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.impl.ClothesAttributeDefServiceImpl;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import java.util.List;
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

  // 실제 중복 삽입 시나리오를 테스트하기 위해 서비스 통합 테스트로 검증
  @Test
  @DisplayName("실제 이름 UNIQUE 위반을 중복 이름 예외로 변환한다")
  void 이름_UNIQUE_위반_예외_변환() {
    // given
    clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(
        " 색상 ",
        List.of("화이트", "핑크")
    );

    // when & then
    assertThatThrownBy(() -> clothesAttributeDefService.create(command))
        .isInstanceOf(DuplicateClothesAttributeDefNameException.class)
        .hasMessageContaining("이미 존재하는 의상 속성 정의 이름입니다");
  }
}
