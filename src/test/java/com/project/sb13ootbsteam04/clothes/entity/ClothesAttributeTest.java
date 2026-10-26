package com.project.sb13ootbsteam04.clothes.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("의상 속성 엔티티")
class ClothesAttributeTest {

  @Test
  @DisplayName("생성하면 소속 의상, 속성 정의, 속성값을 보존한다")
  void 의상_속성_생성_성공() {
    // given
    Clothes clothes = Clothes.create(
        UUID.randomUUID(),
        "블랙 셔츠",
        ClothesType.TOP,
        "https://example.com/shirt.jpg"
    );
    ClothesAttributeDef clothesAttributeDef = ClothesAttributeDef.create("색상", List.of("블랙"));
    String value = " 블랙 ";

    // when
    ClothesAttribute attribute = ClothesAttribute.create(clothes, clothesAttributeDef, value);

    // then
    assertAll(
        () -> assertSame(clothes, attribute.getClothes(), "소속 의상"),
        () -> assertEquals(clothesAttributeDef, attribute.getDefinition(), "속성 정의"),
        () -> assertEquals("블랙", attribute.getValue(), "속성값")
    );
  }

  @Test
  @DisplayName("속성을 생성하면 소속 의상의 목록에도 한 번 연결된다")
  void 속성_생성_시_양방향_연결() {
    Clothes clothes = Clothes.create(UUID.randomUUID(), "셔츠", ClothesType.TOP, null);
    ClothesAttributeDef clothesAttributeDef = ClothesAttributeDef.create("색상", List.of("블랙"));

    ClothesAttribute attribute = ClothesAttribute.create(clothes, clothesAttributeDef, "블랙");

    assertAll(
        () -> assertSame(clothes, attribute.getClothes()),
        () -> assertEquals(List.of(attribute), clothes.getAttributes())
    );
  }

  @Test
  @DisplayName("소속 의상, 속성 정의 또는 값이 없으면 생성에 실패하고 부모 목록은 유지된다")
  void 필수값_누락_시_연결하지_않음() {
    Clothes clothes = Clothes.create(UUID.randomUUID(), "셔츠", ClothesType.TOP, null);
    ClothesAttributeDef clothesAttributeDef = ClothesAttributeDef.create("색상", List.of("블랙"));
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> ClothesAttribute.create(clothes, null, "블랙")),
        () -> assertThrows(NullPointerException.class, () -> ClothesAttribute.create(clothes, clothesAttributeDef, null)),
        () -> assertThrows(NullPointerException.class, () -> ClothesAttribute.create(null, clothesAttributeDef, "블랙")),
        () -> assertTrue(clothes.getAttributes().isEmpty())
    );
  }

  @Test
  @DisplayName("속성 정의에 없는 값을 선택하면 생성에 실패하고 부모 목록은 유지된다")
  void 선택할_수_없는_속성값_거부() {
    Clothes clothes = Clothes.create(UUID.randomUUID(), "셔츠", ClothesType.TOP, null);
    ClothesAttributeDef definition = ClothesAttributeDef.create("색상", List.of("블랙", "크림"));

    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> ClothesAttribute.create(clothes, definition, "블루")),
        () -> assertTrue(clothes.getAttributes().isEmpty())
    );
  }

  @Test
  @DisplayName("대소문자가 다른 값은 정의에 저장된 표기로 정규화한다")
  void 속성값_표기_정규화() {
    Clothes clothes = Clothes.create(UUID.randomUUID(), "셔츠", ClothesType.TOP, null);
    ClothesAttributeDef definition = ClothesAttributeDef.create("색상", List.of("Black"));

    ClothesAttribute attribute = ClothesAttribute.create(clothes, definition, " black ");

    assertEquals("Black", attribute.getValue());
  }
}
