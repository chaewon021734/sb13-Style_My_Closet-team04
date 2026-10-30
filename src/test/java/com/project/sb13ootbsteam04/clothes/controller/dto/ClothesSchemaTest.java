package com.project.sb13ootbsteam04.clothes.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("의상 OpenAPI 스키마")
class ClothesSchemaTest {

  @Test
  @DisplayName("의상 ERD 문자열 길이를 문서화한다")
  void ERD_문자열_길이_문서화() {
    // given
    Map<String, Schema> schemas = ModelConverters.getInstance().read(ClothesDto.class);
    Schema<?> clothesDto = schemas.get("ClothesDto");

    // when & then
    assertAll(
        () -> assertThat(property(clothesDto, "name").getMaxLength()).isEqualTo(255),
        () -> assertThat(property(clothesDto, "imageUrl").getMaxLength()).isEqualTo(500)
    );
  }

  private Schema<?> property(Schema<?> schema, String name) {
    return (Schema<?>) schema.getProperties().get(name);
  }
}
