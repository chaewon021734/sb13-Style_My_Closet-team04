package com.project.sb13ootbsteam04.clothes.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("의상 엔티티")
class ClothesTest {

  @Test
  @DisplayName("의상을 생성하면 소유자, 이름, 종류가 설정된다")
  void 의상_생성_성공() {
    // given
    UUID ownerId = UUID.randomUUID();
    String name = "테스트 의상";
    ClothesType type = ClothesType.TOP;

    // when
    Clothes clothes = Clothes.create(ownerId, name, type);

    // then
    assertAll(
        () -> assertEquals(ownerId, clothes.getOwnerId(), "의상 소유자"),
        () -> assertEquals(name, clothes.getName(), "의상 이름"),
        () -> assertEquals(type, clothes.getType(), "의상 종류")
    );
  }
}
