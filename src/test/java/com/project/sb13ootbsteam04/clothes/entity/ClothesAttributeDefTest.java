package com.project.sb13ootbsteam04.clothes.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
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
  @DisplayName("이름과 선택 가능한 값의 앞뒤 공백을 제거하고 입력 목록을 복사한다")
  void 의상_속성_정의_입력_정규화() {
    // given
    List<String> selectableValues = new ArrayList<>(List.of(" 블랙 ", " 크림 "));

    // when
    ClothesAttributeDef definition = ClothesAttributeDef.create(" 색상 ", selectableValues);
    selectableValues.add("화이트");

    // then
    assertAll(
        () -> assertEquals("색상", definition.getName()),
        () -> assertEquals(List.of("블랙", "크림"), definition.getSelectableValues()),
        () -> assertThrows(
            UnsupportedOperationException.class,
            () -> definition.getSelectableValues().add("화이트")
        )
    );
  }

  @Test
  @DisplayName("이름 또는 선택 가능한 값이 없거나 비어 있으면 생성할 수 없다")
  void 의상_속성_정의_필수값_검증() {
    // when & then
    assertAll(
        () -> assertThrows(
            NullPointerException.class,
            () -> ClothesAttributeDef.create(null, List.of("블랙"))
        ),
        () -> assertThrows(
            IllegalArgumentException.class,
            () -> ClothesAttributeDef.create(" ", List.of("블랙"))
        ),
        () -> assertThrows(
            NullPointerException.class,
            () -> ClothesAttributeDef.create("색상", null)
        ),
        () -> assertThrows(
            IllegalArgumentException.class,
            () -> ClothesAttributeDef.create("색상", List.of())
        ),
        () -> assertThrows(
            NullPointerException.class,
            () -> ClothesAttributeDef.create("색상", Collections.singletonList(null))
        ),
        () -> assertThrows(
            IllegalArgumentException.class,
            () -> ClothesAttributeDef.create("색상", List.of(" "))
        )
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

  @Test
  @DisplayName("속성 이름 255자와 선택 가능한 값 100자는 허용한다")
  void 속성_이름과_선택값_최대_길이_허용() {
    // when
    ClothesAttributeDef definition = ClothesAttributeDef.create(
        "가".repeat(255),
        List.of("나".repeat(100))
    );

    // then
    assertAll(
        () -> assertEquals(255, definition.getName().length()),
        () -> assertEquals(100, definition.getSelectableValues().get(0).length())
    );
  }
}
