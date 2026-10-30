package com.project.sb13ootbsteam04.clothes.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDto;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesDto;
import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("의상 매퍼")
class ClothesMapperTest {

  private final ClothesMapper mapper = Mappers.getMapper(ClothesMapper.class);

  @Test
  @DisplayName("등록 요청을 서비스 Command로 변환한다")
  void 등록_요청_Command_변환() {
    UUID ownerId = UUID.randomUUID();
    UUID definitionId = UUID.randomUUID();

    ClothesCreateRequest request = new ClothesCreateRequest(
        ownerId,
        "셔츠",
        ClothesType.TOP,
        List.of(new ClothesAttributeDto(definitionId, "블랙"))
    );

    ClothesCreateCommand command = mapper.toCommand(request);

    assertAll(
        () -> assertThat(command.ownerId()).isEqualTo(ownerId),
        () -> assertThat(command.name()).isEqualTo("셔츠"),
        () -> assertThat(command.type()).isEqualTo(ClothesType.TOP),
        () -> assertThat(command.image()).isNull(),
        () -> assertThat(command.attributes()).hasSize(1),
        () -> assertThat(command.attributes().get(0).definitionId()).isEqualTo(definitionId),
        () -> assertThat(command.attributes().get(0).value()).isEqualTo("블랙")
    );
  }

  @Test
  @DisplayName("의상 엔티티를 서비스 결과와 Swagger 응답 구조로 변환한다")
  void 엔티티_Result_DTO_변환() {
    UUID ownerId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();
    UUID definitionId = UUID.randomUUID();

    User owner = new User("소유자", "owner@example.com", "encoded-password");
    ReflectionTestUtils.setField(owner, "id", ownerId);
    Clothes clothes = Clothes.create(owner, "셔츠", ClothesType.TOP, "https://cdn.example.com/image.png");
    ClothesAttributeDef definition = ClothesAttributeDef.create("색상", List.of("블랙", "크림"));
    ReflectionTestUtils.setField(clothes, "id", clothesId);
    ReflectionTestUtils.setField(definition, "id", definitionId);

    ClothesAttribute.create(clothes, definition, "블랙");

    ClothesResult result = mapper.toResult(clothes);
    ClothesDto dto = mapper.toDto(result);

    assertAll(
        () -> assertThat(dto.id()).isEqualTo(clothesId),
        () -> assertThat(dto.ownerId()).isEqualTo(ownerId),
        () -> assertThat(dto.name()).isEqualTo("셔츠"),
        () -> assertThat(dto.imageUrl()).isEqualTo("https://cdn.example.com/image.png"),
        () -> assertThat(dto.type()).isEqualTo(ClothesType.TOP),
        () -> assertThat(dto.attributes()).singleElement().satisfies(attribute ->
            assertAll(
                () -> assertThat(attribute.definitionId()).isEqualTo(definitionId),
                () -> assertThat(attribute.definitionName()).isEqualTo("색상"),
                () -> assertThat(attribute.selectableValues()).containsExactly("블랙", "크림"),
                () -> assertThat(attribute.value()).isEqualTo("블랙")
            ))
    );
  }
}
