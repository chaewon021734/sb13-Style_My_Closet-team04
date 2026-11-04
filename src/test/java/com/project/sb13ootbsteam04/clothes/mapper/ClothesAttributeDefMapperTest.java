package com.project.sb13ootbsteam04.clothes.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefDto;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("의상 속성 정의 매퍼")
class ClothesAttributeDefMapperTest {

  private final ClothesAttributeDefMapper mapper = Mappers.getMapper(ClothesAttributeDefMapper.class);

  @Test
  @DisplayName("등록 요청을 서비스 Command로 변환한다")
  void 등록_요청_Command_변환() {
    // given
    ClothesAttributeDefCreateRequest request = new ClothesAttributeDefCreateRequest("색상", List.of("블랙", "크림"));

    // when
    ClothesAttributeDefCreateCommand command = mapper.toCommand(request);

    // then
    assertAll(
        () -> assertThat(command.name()).isEqualTo("색상"),
        () -> assertThat(command.selectableValues()).containsExactly("블랙", "크림")
    );
  }

  @Test
  @DisplayName("서비스 결과를 Swagger 응답 DTO로 변환한다")
  void Result_DTO_변환() {
    // given
    UUID definitionId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-28T00:00:00Z");
    ClothesAttributeDefResult result = new ClothesAttributeDefResult(
        definitionId,
        "색상",
        List.of("블랙", "크림"),
        createdAt
    );

    // when
    ClothesAttributeDefDto dto = mapper.toDto(result);

    // then
    assertAll(
        () -> assertThat(dto.id()).isEqualTo(definitionId),
        () -> assertThat(dto.name()).isEqualTo("색상"),
        () -> assertThat(dto.selectableValues()).containsExactly("블랙", "크림"),
        () -> assertThat(dto.createdAt()).isEqualTo(createdAt)
    );
  }
}
