package com.project.sb13ootbsteam04.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
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
@DisplayName("의상 저장소")
class ClothesRepositoryTest {

  @Autowired
  private ClothesRepository clothesRepository;

  @Autowired
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("이미 저장된 사용자를 소유자로 연결해 의상을 저장한다")
  void 저장된_사용자와_의상_연관관계_저장() {
    // given
    User owner = userRepository.saveAndFlush(new User("소유자", "owner@example.com", "encoded-password"));
    UUID ownerId = owner.getId();
    entityManager.clear();

    User detachedOwner = userRepository.findById(ownerId).orElseThrow();
    entityManager.detach(detachedOwner);

    // when
    Clothes saved = clothesRepository.saveAndFlush(Clothes.create(detachedOwner, "셔츠", ClothesType.TOP, null));
    UUID clothesId = saved.getId();
    entityManager.clear();

    Clothes found = clothesRepository.findById(clothesId).orElseThrow();

    // then
    assertAll(
        () -> assertThat(found.getOwner().getId()).isEqualTo(ownerId),
        () -> assertThat(found.getName()).isEqualTo("셔츠"),
        () -> assertThat(found.getType()).isEqualTo(ClothesType.TOP)
    );
  }

  @Test
  @DisplayName("한 의상에 같은 속성 정의를 중복 저장할 수 없다")
  void 같은_의상의_속성_정의_중복_저장_실패() {
    // given
    User owner = userRepository.saveAndFlush(new User("소유자", "duplicate-owner@example.com", "encoded-password"));
    ClothesAttributeDef definition = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));
    Clothes clothes = Clothes.create(owner, "셔츠", ClothesType.TOP, null);
    ClothesAttribute.create(clothes, definition, "블랙");
    ClothesAttribute.create(clothes, definition, "크림");

    // when & then
    assertThatThrownBy(() -> clothesRepository.saveAndFlush(clothes))
        .isInstanceOf(DataIntegrityViolationException.class)
        .satisfies(exception -> assertThat(
            ((DataIntegrityViolationException) exception).getMostSpecificCause())
            .isInstanceOfSatisfying(
                SQLException.class,
                sqlException -> assertThat(sqlException.getSQLState()).isEqualTo("23505")
            ));
  }
}
