package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapperImpl;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCursorResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesSearchCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesUpdateCommand;
import com.project.sb13ootbsteam04.clothes.service.impl.ClothesServiceImpl;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.StoredImage;
import com.project.sb13ootbsteam04.global.config.JpaAuditingConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, ClothesServiceImpl.class, ClothesMapperImpl.class})
class ClothesUpdateIntegrationTest {

  @Autowired private ClothesService clothesService;
  @Autowired private TransactionOperations transactions;
  @Autowired private EntityManager entityManager;
  @MockitoBean private ClothesImageStorage storage;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void 이미지와_속성을_수정하면_기존_속성_행을_유지하고_변경사항을_커밋한다() {
    // given
    User owner = new User("소유자", UUID.randomUUID() + "@example.com", "password");
    Clothes clothes = Clothes.create(owner, "코트", ClothesType.OUTER, null);
    ClothesAttributeDef size = ClothesAttributeDef.create("사이즈", List.of("S", "M"));
    ClothesAttributeDef color = ClothesAttributeDef.create("색상", List.of("블랙"));
    ClothesAttribute original = ClothesAttribute.create(clothes, size, "S");

    transactions.executeWithoutResult(status -> {
      entityManager.persist(owner);
      entityManager.persist(size);
      entityManager.persist(color);
      entityManager.persist(clothes);
    });

    ClothesImageCommand image = new ClothesImageCommand("new.jpg", "image/jpeg", new byte[]{-1, -40, -1});
    StoredImage uploaded = new StoredImage("clothes/new.jpg", "https://cdn.example.com/clothes/new.jpg");
    given(storage.upload(image)).willReturn(uploaded);

    // when: 컨트롤러에서 호출할 때처럼 서비스가 직접 트랜잭션을 열고 커밋한다.
    ClothesResult result = clothesService.update(new ClothesUpdateCommand(clothes.getId(), "새 코트", null, List.of(
        new ClothesAttributeCommand(size.getId(), "m"), new ClothesAttributeCommand(color.getId(), "블랙")), image));

    // then
    assertThat(result.id()).isEqualTo(clothes.getId());
    assertThat(result.imageUrl()).isEqualTo(uploaded.url());

    // 수정 트랜잭션이 끝난 뒤 소유자의 목록에도 같은 의상과 속성값이 반환되어야 한다.
    ClothesCursorResult page = clothesService.search(new ClothesSearchCommand(null, null, 10, ClothesType.OUTER, owner.getId()));
    assertThat(page.data()).singleElement().satisfies(listed -> {
      assertThat(listed.id()).isEqualTo(result.id());
      assertThat(listed.name()).isEqualTo("새 코트");
      assertThat(listed.imageUrl()).isEqualTo(uploaded.url());
      assertThat(listed.attributes()).containsExactlyInAnyOrderElementsOf(result.attributes());
    });

    transactions.executeWithoutResult(status -> {
      Clothes persisted = entityManager.find(Clothes.class, clothes.getId());
      assertThat(persisted.getImageUrl()).isEqualTo(uploaded.url());
      assertThat(persisted.getName()).isEqualTo("새 코트");
      assertThat(entityManager.find(ClothesAttribute.class, original.getId()).getValue()).isEqualTo("M");
      assertThat(persisted.getAttributes()).extracting(ClothesAttribute::getValue).containsExactlyInAnyOrder("M", "블랙");
      entityManager.remove(persisted);
      entityManager.remove(persisted.getOwner());
      entityManager.remove(entityManager.getReference(ClothesAttributeDef.class, size.getId()));
      entityManager.remove(entityManager.getReference(ClothesAttributeDef.class, color.getId()));
    });

    then(storage).should().upload(image);
    then(storage).shouldHaveNoMoreInteractions();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void 이미지_교체가_롤백되면_기존_URL을_보존하고_새_객체만_삭제한다() {
    // given
    String originalUrl = "https://cdn.example.com/clothes/old.png";
    UUID clothesId = transactions.execute(status -> {
      User owner = new User("소유자", UUID.randomUUID() + "@example.com", "password");
      entityManager.persist(owner);
      Clothes clothes = Clothes.create(owner, "기존 옷", ClothesType.TOP, originalUrl);
      entityManager.persist(clothes);
      return clothes.getId();
    });

    ClothesImageCommand image = new ClothesImageCommand("new.jpg", "image/jpeg", new byte[]{-1, -40, -1});
    StoredImage uploaded = new StoredImage("clothes/new.jpg", "https://cdn.example.com/clothes/new.jpg");
    given(storage.upload(image)).willReturn(uploaded);
    ClothesUpdateCommand command = new ClothesUpdateCommand(clothesId, "새 이름", null, null, image);

    RuntimeException failure = new IllegalStateException("후속 작업 실패");

    // when & then: 변경 SQL이 실행된 뒤 외부 트랜잭션에서 실패해도 함께 복구해야 한다.
    assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
      assertThat(clothesService.update(command).imageUrl()).isEqualTo(uploaded.url());
      entityManager.flush();
      throw failure;
    })).isSameAs(failure);

    transactions.executeWithoutResult(status -> {
      Clothes persisted = entityManager.find(Clothes.class, clothesId);
      assertThat(persisted.getImageUrl()).isEqualTo(originalUrl);
      assertThat(persisted.getName()).isEqualTo("기존 옷");
      entityManager.remove(persisted);
      entityManager.remove(persisted.getOwner());
    });

    then(storage).should().upload(image);
    then(storage).should().delete(uploaded.objectKey());
    then(storage).shouldHaveNoMoreInteractions();
  }
}
