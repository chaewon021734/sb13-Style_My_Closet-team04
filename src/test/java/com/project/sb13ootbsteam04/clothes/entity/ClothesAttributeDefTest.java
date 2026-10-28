package com.project.sb13ootbsteam04.clothes.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
