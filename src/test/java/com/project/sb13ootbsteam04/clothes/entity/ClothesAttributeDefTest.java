package com.project.sb13ootbsteam04.clothes.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("의상 속성 정의 엔티티")
class ClothesAttributeDefTest {

  @Test
  @DisplayName("생성하면 속성 정의 이름과 선택 가능한 옵션 값을 보존한다")
  void 의상_속성_정의_생성_성공() {
    // given
    String name = "색상";
    List<String> selectableValues = List.of("블랙", "화이트");

    // when
    ClothesAttributeDef definition = ClothesAttributeDef.create(name, selectableValues);

    // then
    assertAll(
        () -> assertEquals(name, definition.getName(), "속성 정의 이름"),
        () -> assertEquals(
            selectableValues, definition.getSelectableValues(), "선택 가능한 옵션 값")
    );
  }

  @Test
  @DisplayName("선택 가능한 값은 공백과 대소문자를 무시하고 중복될 수 없다")
  void 선택_가능한_값_중복_실패() {
    // given
    List<String> duplicatedValues = List.of("Black", " black ");

    // when & then
    assertThrows(
        IllegalArgumentException.class,
        () -> ClothesAttributeDef.create("색상", duplicatedValues)
    );
  }

  @Test
  @DisplayName("속성 이름과 선택 가능한 값은 ERD 최대 길이를 초과할 수 없다")
  void 속성_이름과_선택값_길이_검증() {
    // when & then
    assertAll(
        () -> assertThrows(
            IllegalArgumentException.class,
            () -> ClothesAttributeDef.create("가".repeat(256), List.of("블랙"))
        ),
        () -> assertThrows(
            IllegalArgumentException.class,
            () -> ClothesAttributeDef.create("색상", List.of("가".repeat(101)))
        )
    );
  }
}
