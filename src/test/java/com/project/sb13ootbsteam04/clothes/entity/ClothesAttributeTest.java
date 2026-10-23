package com.project.sb13ootbsteam04.clothes.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("의상 속성 엔티티")
public class ClothesAttributeTest {

  @Test
  @DisplayName("생성하면 소속 의상 및 속성 정의 ID, 속성값을 보존한다")
  void 의상_속성_생성_성공() {
    // given
    Clothes clothes = Clothes.create(
        UUID.randomUUID(),
        "검정 셔츠",
        ClothesType.TOP,
        "https://example.com/shirt.jpg"
    );
    UUID definitionId = UUID.randomUUID();
    String value="검정";

    // when
    ClothesAttribute attribute = ClothesAttribute.create(clothes, definitionId, value);

    // then
    assertAll(
        () -> assertEquals(clothes, attribute.getClothes(), "소속 의상"),
        () -> assertEquals(definitionId, attribute.getDefinitionId(), "속성 정의 ID"),
        () -> assertEquals(value, attribute.getValue(), "속성값")
    );
  }
}
