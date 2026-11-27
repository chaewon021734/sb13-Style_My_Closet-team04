package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionOperations;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("의상 삭제 통합 테스트")
class ClothesDeleteIntegrationTest {

  @Autowired private ClothesService service;
  @Autowired private TransactionOperations transactions;
  @Autowired private EntityManager em;
  @MockitoBean private ClothesImageStorage storage;

  @Test
  @DisplayName("삭제를 커밋하면 대상 옷과 속성만 제거하고 이미지 키를 정리한다")
  void 옷_삭제_커밋() {
    // given
    Clothes target = saveClothesWithImageAndAttribute();
    User owner = target.getOwner();
    ClothesAttribute attribute = target.getAttributes().get(0);
    ClothesAttributeDef definition = attribute.getDefinition();
    // 삭제 대상 옷과 같은 정의를 사용하는 다른 옷과 그 선택값은 보존되어야 한다.
    Clothes other = Clothes.create(owner, "남길 옷", ClothesType.TOP);
    ClothesAttribute otherAttribute = ClothesAttribute.create(other, definition, "M");
    transactions.executeWithoutResult(status -> em.persist(other));

    // when
    transactions.executeWithoutResult(status -> {
      service.delete(owner.getId(), target.getId());
      then(storage).shouldHaveNoInteractions();
    });

    // then
    then(storage).should().delete(target.getImageObjectKey());
    transactions.executeWithoutResult(status -> {
      assertThat(em.find(Clothes.class, target.getId())).isNull();
      assertThat(em.find(ClothesAttribute.class, attribute.getId())).isNull();
      assertThat(em.find(User.class, owner.getId())).isNotNull();
      assertThat(em.find(ClothesAttributeDef.class, definition.getId())).isNotNull();
      assertThat(em.find(Clothes.class, other.getId())).isNotNull();
      assertThat(em.find(ClothesAttribute.class, otherAttribute.getId()).getValue()).isEqualTo("M");
    });
  }

  @Test
  @DisplayName("삭제를 롤백하면 옷과 속성은 유지되고 이미지 저장소는 호출하지 않는다")
  void 옷_삭제_롤백() {
    // given
    Clothes target = saveClothesWithImageAndAttribute();
    UUID attributeId = target.getAttributes().get(0).getId();

    // when
    transactions.executeWithoutResult(status -> {
      service.delete(target.getOwner().getId(), target.getId());
      em.flush(); // 실제 DELETE가 실행된 뒤 롤백되는 경로를 검증한다.
      status.setRollbackOnly();
    });

    // then
    then(storage).shouldHaveNoInteractions();
    transactions.executeWithoutResult(status -> {
      Clothes remaining = em.find(Clothes.class, target.getId());
      assertThat(remaining.getImageObjectKey()).isEqualTo(target.getImageObjectKey());
      assertThat(em.find(ClothesAttribute.class, attributeId).getValue()).isEqualTo("M");
    });
  }

  @Test
  @DisplayName("이미지 저장소 삭제가 실패해도 커밋된 의상 삭제는 성공한다")
  void 저장소_정리_실패() {
    // given
    Clothes clothes = saveClothesWithImageAndAttribute();
    String key = clothes.getImageObjectKey();
    willThrow(new IllegalStateException("저장소 연결 실패")).given(storage).delete(key);

    // when
    service.delete(clothes.getOwner().getId(), clothes.getId());

    // then
    then(storage).should().delete(key);
    transactions.executeWithoutResult(status -> assertThat(em.find(Clothes.class, clothes.getId())).isNull());
  }

  private Clothes saveClothesWithImageAndAttribute() {
    User owner = new User("소유자", UUID.randomUUID() + "@example.com", "password");
    Clothes clothes = Clothes.create(owner, "코트", ClothesType.OUTER);
    ClothesAttributeDef definition = ClothesAttributeDef.create("크기-" + UUID.randomUUID(), List.of("M"));
    ClothesAttribute.create(clothes, definition, "M");
    String key = "clothes/" + UUID.randomUUID();
    clothes.updateImage("https://cdn.example.com/" + key, key);
    transactions.executeWithoutResult(status -> {
      em.persist(owner);
      em.persist(definition);
      em.persist(clothes);
    });
    return clothes;
  }
}
