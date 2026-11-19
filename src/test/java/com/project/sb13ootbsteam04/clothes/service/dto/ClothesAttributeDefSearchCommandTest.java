package com.project.sb13ootbsteam04.clothes.service.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefSortBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

@DisplayName("의상 속성 정의 조회 조건")
class ClothesAttributeDefSearchCommandTest {

  @Test
  @DisplayName("정렬 기준이 없으면 조회 조건을 생성할 수 없다")
  void 정렬_기준_누락() {
    // when & then
    assertThatThrownBy(() -> new ClothesAttributeDefSearchCommand((ClothesAttributeDefSortBy) null, Direction.ASC, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("정렬 기준은 필수입니다");
  }

  @Test
  @DisplayName("API 정렬 기준 문자열이 없으면 조회 조건을 생성할 수 없다")
  void API_정렬_기준_누락() {
    // when & then
    assertThatThrownBy(() -> new ClothesAttributeDefSearchCommand((String) null, Direction.ASC, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("정렬 기준은 필수입니다");
  }

  @Test
  @DisplayName("정렬 방향이 없으면 조회 조건을 생성할 수 없다")
  void 정렬_방향_누락() {
    // when & then
    assertThatThrownBy(() -> new ClothesAttributeDefSearchCommand(ClothesAttributeDefSortBy.NAME, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("정렬 방향은 필수입니다");
  }
}
