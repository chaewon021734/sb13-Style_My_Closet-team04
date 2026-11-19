package com.project.sb13ootbsteam04.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchCondition;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchResult;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@DisplayName("의상 저장소")
class ClothesRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ClothesRepository clothesRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("소유자와 의상 타입으로 필터링해 최신 등록순으로 커서 페이지를 조회한다")
  void 소유자와_타입으로_필터링해_최신순_조회() {
    // given
    User owner = userRepository.saveAndFlush(new User("테스트 사용자", "owner@test.com", "Test1234!@"));
    User anotherOwner = userRepository.saveAndFlush(new User("다른 사용자", "another@test.com", "Test1234!@"));

    Clothes oldestTop = saveClothes(owner, "오래된 상의", ClothesType.TOP);
    Clothes bottom = saveClothes(owner, "하의", ClothesType.BOTTOM);
    Clothes middleTop = saveClothes(owner, "중간에 등록한 상의", ClothesType.TOP);
    Clothes newestTop = saveClothes(owner, "최신 상의", ClothesType.TOP);
    Clothes anotherOwnersTop = saveClothes(anotherOwner, "다른 사용자의 상의", ClothesType.TOP);

    Instant baseTime = Instant.parse("2026-11-04T00:00:00Z");
    updateCreatedAt(oldestTop.getId(), baseTime);
    updateCreatedAt(bottom.getId(), baseTime.plusSeconds(60));
    updateCreatedAt(middleTop.getId(), baseTime.plusSeconds(120));
    updateCreatedAt(newestTop.getId(), baseTime.plusSeconds(180));
    updateCreatedAt(anotherOwnersTop.getId(), baseTime.plusSeconds(240));
    ClothesSearchCondition condition = new ClothesSearchCondition(
        null,
        null,
        2,
        ClothesType.TOP,
        owner.getId()
    );
    entityManager.clear();

    // when
    ClothesSearchResult result = clothesRepository.search(condition);

    // then
    assertAll(
        () -> assertThat(result.content()).extracting(Clothes::getId).containsExactly(newestTop.getId(), middleTop.getId()),
        () -> assertThat(result.hasNext()).isTrue(),
        () -> assertThat(result.totalCount()).isEqualTo(3)
    );
  }

  private Clothes saveClothes(User owner, String name, ClothesType type) {
    return clothesRepository.saveAndFlush(Clothes.create(owner, name, type, null));
  }

  private void updateCreatedAt(UUID clothesId, Instant createdAt) {
    entityManager.createNativeQuery(
        "UPDATE clothes SET created_at = :createdAt WHERE id = :clothesId")
        .setParameter("createdAt", createdAt)
        .setParameter("clothesId", clothesId)
        .executeUpdate();
    entityManager.flush();
  }
}
