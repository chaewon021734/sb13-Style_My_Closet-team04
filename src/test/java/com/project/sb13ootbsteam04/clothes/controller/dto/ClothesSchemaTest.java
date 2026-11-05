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
    Schema<?> clothesCreateRequest = schema(ClothesCreateRequest.class, "ClothesCreateRequest");
    Schema<?> clothesDto = schema(ClothesDto.class, "ClothesDto");
    Schema<?> attributeDto = schema(ClothesAttributeDto.class, "ClothesAttributeDto");
    Schema<?> attributeWithDefDto = schema(
        ClothesAttributeWithDefDto.class,
        "ClothesAttributeWithDefDto"
    );

    // when & then
    assertAll(
        () -> assertThat(property(clothesCreateRequest, "name").getMaxLength()).isEqualTo(255),
        () -> assertThat(property(clothesDto, "name").getMaxLength()).isEqualTo(255),
        () -> assertThat(property(clothesDto, "imageUrl").getMaxLength()).isEqualTo(500),
        () -> assertThat(property(attributeDto, "value").getMaxLength()).isEqualTo(100),
        () -> assertThat(property(attributeWithDefDto, "definitionName").getMaxLength()).isEqualTo(255),
        () -> assertThat(property(attributeWithDefDto, "selectableValues").getItems().getMaxLength()).isEqualTo(100),
        () -> assertThat(property(attributeWithDefDto, "value").getMaxLength()).isEqualTo(100)
    );
  }

  private Schema<?> schema(Class<?> type, String schemaName) {
    Map<String, Schema> schemas = ModelConverters.getInstance().read(type);
    return schemas.get(schemaName);
  }

  private Schema<?> property(Schema<?> schema, String name) {
    return (Schema<?>) schema.getProperties().get(name);
  }
}
