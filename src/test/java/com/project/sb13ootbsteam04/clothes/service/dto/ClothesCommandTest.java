package com.project.sb13ootbsteam04.clothes.service.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("의상 생성")
class ClothesCommandTest {

  @Test
  @DisplayName("API 외부 호출에서도 문자열을 정규화하고 선택 속성 목록을 빈 목록으로 만든다")
  void Command_정규화() {
    ClothesCreateCommand command = new ClothesCreateCommand(UUID.randomUUID(), "  셔츠  ", ClothesType.TOP, null, null);
    ClothesAttributeCommand attribute = new ClothesAttributeCommand(UUID.randomUUID(), "  블랙  ");

    assertAll(
        () -> assertThat(command.name()).isEqualTo("셔츠"),
        () -> assertThat(command.attributes()).isEmpty(),
        () -> assertThat(attribute.value()).isEqualTo("블랙")
    );
  }

  @Test
  @DisplayName("엔티티 컬럼 길이를 초과한 값은 서비스 진입 전에 거부한다")
  void 길이_검증() {
    UUID id = UUID.randomUUID();

    assertAll(
        () -> assertThatThrownBy(() -> new ClothesCreateCommand(
            id, "가".repeat(256), ClothesType.TOP, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("의상 이름은 255자까지만 입력 가능합니다"),
        () -> assertThatThrownBy(() -> new ClothesAttributeCommand(id, "나".repeat(101)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("속성 값은 100자까지만 입력 가능합니다")
    );
  }
}
