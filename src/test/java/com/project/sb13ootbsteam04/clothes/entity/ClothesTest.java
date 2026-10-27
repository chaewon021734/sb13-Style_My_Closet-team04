package com.project.sb13ootbsteam04.clothes.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.project.sb13ootbsteam04.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("의상 엔티티")
class ClothesTest {

  @Test
  @DisplayName("의상을 생성하면 소유자, 이름, 종류, 이미지 URL이 설정된다")
  void 의상_생성_성공() {
    // given
    User owner = owner();
    String name = "테스트 의상";
    ClothesType type = ClothesType.TOP;
    String imageUrl = "https://example.com/clothes.jpg";

    // when
    Clothes clothes = Clothes.create(owner, name, type, imageUrl);

    // then
    assertAll(
        () -> assertEquals(owner, clothes.getOwner(), "의상 소유자"),
        () -> assertEquals(name, clothes.getName(), "의상 이름"),
        () -> assertEquals(type, clothes.getType(), "의상 종류"),
        () -> assertEquals(imageUrl, clothes.getImageUrl(), "의상 이미지 URL")
    );
  }

  @Test
  @DisplayName("이미지와 속성 없이 의상을 생성할 수 있다")
  void 이미지와_속성_없이_생성() {
    Clothes clothes = clothes();

    assertAll(
        () -> assertNull(clothes.getImageUrl()),
        () -> assertTrue(clothes.getAttributes().isEmpty())
    );
  }

  @Test
  @DisplayName("필수값이 없거나 이름이 비어 있으면 의상을 생성할 수 없다")
  void 의상_필수값_검증() {
    User owner = owner();

    assertAll(
        () -> assertThrows(NullPointerException.class, () -> Clothes.create(null, "셔츠", ClothesType.TOP, null)),
        () -> assertThrows(NullPointerException.class, () -> Clothes.create(owner, null, ClothesType.TOP, null)),
        () -> assertThrows(IllegalArgumentException.class, () -> Clothes.create(owner, " ", ClothesType.TOP, null)),
        () -> assertThrows(NullPointerException.class, () -> Clothes.create(owner, "셔츠", null, null))
    );
  }

  @Test
  @DisplayName("이미지 URL을 정규화하고 빈 URL은 거부한다")
  void 이미지_URL_수정() {
    Clothes clothes = clothes();

    clothes.updateImageUrl(" https://cdn.example.com/image.png ");

    assertAll(
        () -> assertEquals("https://cdn.example.com/image.png", clothes.getImageUrl()),
        () -> assertThrows(IllegalArgumentException.class,() -> clothes.updateImageUrl(" "))
    );
  }

  private Clothes clothes() {
    return Clothes.create(owner(), "셔츠", ClothesType.TOP, null);
  }

  private User owner() {
    return new User("소유자", "owner@example.com", "encoded-password");
  }
}
