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
  @DisplayName("의상 생성 후 업로드한 이미지를 연결한다")
  void 의상_생성_성공() {
    // given
    User owner = owner();
    String name = "테스트 의상";
    ClothesType type = ClothesType.TOP;
    String imageUrl = "https://example.com/clothes.jpg";

    // when
    Clothes clothes = Clothes.create(owner, name, type);
    clothes.updateImage(imageUrl, "clothes/test.jpg");

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
        () -> assertThrows(NullPointerException.class, () -> Clothes.create(null, "셔츠", ClothesType.TOP)),
        () -> assertThrows(NullPointerException.class, () -> Clothes.create(owner, null, ClothesType.TOP)),
        () -> assertThrows(IllegalArgumentException.class, () -> Clothes.create(owner, " ", ClothesType.TOP)),
        () -> assertThrows(NullPointerException.class, () -> Clothes.create(owner, "셔츠", null))
    );
  }

  @Test
  @DisplayName("이미지 교체는 유효한 URL과 키를 함께 반영하며 실패하면 기존 쌍을 유지한다")
  void 이미지_교체_검증() {
    // given
    Clothes clothes = clothes();
    String key = " " + "가".repeat(498) + " ";
    String url = "x".repeat(499) + "😀";

    // when
    clothes.updateImage(" " + url + " ", key);

    // then
    for (String invalid : new String[]{null, " ", "x".repeat(501)}) {
      assertThrows(IllegalArgumentException.class, () -> clothes.updateImage("new-url", invalid));
      assertThrows(IllegalArgumentException.class, () -> clothes.updateImage(invalid, "new-key"));
    }
    assertEquals(url, clothes.getImageUrl());
    assertEquals(key, clothes.getImageObjectKey());
  }

  @Test
  @DisplayName("의상 이름과 이미지 URL은 ERD 최대 길이를 초과할 수 없다")
  void 의상_문자열_길이_검증() {
    // given
    User owner = owner();

    // when & then
    assertAll(
        () -> assertThrows(
            IllegalArgumentException.class,
            () -> Clothes.create(owner, "가".repeat(256), ClothesType.TOP, null)
        ),
        () -> assertThrows(
            IllegalArgumentException.class,
            () -> Clothes.create(owner, "셔츠", ClothesType.TOP, "a".repeat(501))
        )
    );
  }

  @Test
  @DisplayName("의상 이름과 이미지 URL은 ERD 최대 길이까지 허용한다")
  void 의상_문자열_경계값_허용() {
    // given
    String maxLengthName = "가".repeat(255);
    String maxLengthImageUrl = "a".repeat(500);

    // when
    Clothes clothes = Clothes.create(owner(), maxLengthName, ClothesType.TOP, maxLengthImageUrl);

    // then
    assertAll(
        () -> assertEquals(maxLengthName, clothes.getName()),
        () -> assertEquals(maxLengthImageUrl, clothes.getImageUrl())
    );
  }

  private Clothes clothes() {
    return Clothes.create(owner(), "셔츠", ClothesType.TOP);
  }

  private User owner() {
    return new User("소유자", "owner@example.com", "encoded-password");
  }
}
