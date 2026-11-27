package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeDefNameException;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeRepository;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefUpdateCommand;
import com.project.sb13ootbsteam04.clothes.service.impl.ClothesAttributeDefServiceImpl;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
  private ClothesAttributeRepository clothesAttributeRepository;

  @Autowired
  private ClothesRepository clothesRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private ClothesAttributeDefService clothesAttributeDefService;

  private ClothesAttributeDef existingDefinition;

  @BeforeEach
  void setUp() {
    // 공통 기존 속성 정의
    existingDefinition = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));
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

  @Test
  @DisplayName("속성 정의를 삭제하면 해당 참조만 제거하고 의상과 다른 속성은 유지한다")
  void 삭제_참조_속성_정리() {
    // given
    User owner = userRepository.saveAndFlush(new User("테스터", "clothes-delete-" + UUID.randomUUID() + "@example.com", "encoded-password"));
    ClothesAttributeDef sizeDefinition = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("사이즈", List.of("S", "M", "L")));
    Clothes clothes = Clothes.create(owner, "재킷", ClothesType.OUTER, null);
    ClothesAttribute deletedAttribute = ClothesAttribute.create(clothes, existingDefinition, "블랙");
    ClothesAttribute retainedAttribute = ClothesAttribute.create(clothes, sizeDefinition, "M");
    clothesRepository.saveAndFlush(clothes);
    UUID definitionId = existingDefinition.getId();
    UUID clothesId = clothes.getId();
    UUID deletedAttributeId = deletedAttribute.getId();
    UUID retainedAttributeId = retainedAttribute.getId();
    entityManager.clear();

    // when
    clothesAttributeDefService.delete(definitionId);
    entityManager.flush();
    entityManager.clear();

    // then
    assertAll(
        () -> assertThat(clothesAttributeDefRepository.existsById(definitionId)).isFalse(),
        () -> assertThat(clothesAttributeRepository.existsById(deletedAttributeId)).isFalse(),
        () -> assertThat(clothesAttributeDefRepository.existsById(sizeDefinition.getId())).isTrue(),
        () -> assertThat(clothesAttributeRepository.existsById(retainedAttributeId)).isTrue(),
        () -> assertThat(clothesRepository.findById(clothesId))
            .hasValueSatisfying(saved -> {
              assertThat(saved.getName()).isEqualTo("재킷");
              assertThat(saved.getType()).isEqualTo(ClothesType.OUTER);
              assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
              assertThat(saved.getAttributes()).extracting(ClothesAttribute::getId).containsExactly(retainedAttributeId);
            })
    );
  }
}
