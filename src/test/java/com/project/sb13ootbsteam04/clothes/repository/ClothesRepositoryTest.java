package com.project.sb13ootbsteam04.clothes.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchCondition;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchResult;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

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

  @Test
  @DisplayName("생성일이 같은 의상을 ID 보조 커서로 중복 없이 다음 페이지 조회한다")
  void 동일_생성일_ID_보조_커서_조회() {
    // given
    User owner = userRepository.saveAndFlush(new User("커서 테스트 사용자", "cursor@test.com", "Test1234!@"));
    Clothes first = saveClothes(owner, "커서 의상 1", ClothesType.TOP);
    Clothes second = saveClothes(owner, "커서 의상 2", ClothesType.BOTTOM);
    Clothes third = saveClothes(owner, "커서 의상 3", ClothesType.OUTER);
    Instant sameCreatedAt = Instant.parse("2026-11-04T00:00:00Z");
    updateCreatedAt(first.getId(), sameCreatedAt);
    updateCreatedAt(second.getId(), sameCreatedAt);
    updateCreatedAt(third.getId(), sameCreatedAt);
    entityManager.clear();

    ClothesSearchCondition firstPageCondition = new ClothesSearchCondition(
        null,
        null,
        2,
        null,
        owner.getId()
    );

    // when
    ClothesSearchResult firstPage = clothesRepository.search(firstPageCondition);
    Clothes lastClothes = firstPage.content().get(firstPage.content().size() - 1);
    ClothesSearchResult secondPage = clothesRepository.search(new ClothesSearchCondition(
        lastClothes.getCreatedAt(),
        lastClothes.getId(),
        2,
        null,
        owner.getId()
    ));

    // then
    List<UUID> firstPageIds = firstPage.content().stream().map(Clothes::getId).toList();
    List<UUID> secondPageIds = secondPage.content().stream().map(Clothes::getId).toList();
    List<UUID> allPageIds = Stream.concat(firstPageIds.stream(), secondPageIds.stream()).toList();
    assertAll(
        () -> assertThat(firstPageIds).hasSize(2),
        () -> assertThat(secondPageIds).hasSize(1),
        () -> assertThat(firstPageIds).doesNotContainAnyElementsOf(secondPageIds),
        () -> assertThat(allPageIds).containsExactlyInAnyOrder(first.getId(), second.getId(), third.getId()).doesNotHaveDuplicates(),
        () -> assertThat(firstPage.hasNext()).isTrue(),
        () -> assertThat(secondPage.hasNext()).isFalse(),
        () -> assertThat(firstPage.totalCount()).isEqualTo(3),
        () -> assertThat(secondPage.totalCount()).isEqualTo(3)
    );
  }

  @Test
  @DisplayName("조회한 의상은 영속성 컨텍스트가 종료되어도 속성 정의까지 사용할 수 있다")
  void 의상_속성_정의_함께_조회() {
    // given
    User owner = userRepository.saveAndFlush(new User("속성 조회 사용자", "attribute@test.com", "Test1234!@"));
    ClothesAttributeDef definition = clothesAttributeDefRepository.saveAndFlush(ClothesAttributeDef.create("색상", List.of("블랙", "크림")));
    Clothes clothes = Clothes.create(owner, "속성이 있는 의상", ClothesType.TOP, null);
    ClothesAttribute.create(clothes, definition, "블랙");
    clothesRepository.saveAndFlush(clothes);
    entityManager.clear();

    ClothesSearchCondition condition = new ClothesSearchCondition(
        null,
        null,
        10,
        null,
        owner.getId()
    );

    // when
    ClothesSearchResult result = clothesRepository.search(condition);
    entityManager.clear();

    // then
    Clothes found = result.content().get(0);
    assertAll(
        () -> assertThat(found.getAttributes()).hasSize(1),
        () -> assertThat(found.getAttributes().get(0).getValue()).isEqualTo("블랙"),
        () -> assertThat(found.getAttributes().get(0).getDefinition().getId()).isEqualTo(definition.getId()),
        () -> assertThat(found.getAttributes().get(0).getDefinition().getName()).isEqualTo("색상"),
        () -> assertThat(found.getAttributes().get(0).getDefinition().getSelectableValues()).containsExactly("블랙", "크림")
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
