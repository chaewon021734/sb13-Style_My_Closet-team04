package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("의상 서비스")
@ExtendWith(MockitoExtension.class)
class ClothesServiceTest {

  @Mock
  private ClothesRepository clothesRepository;

  @Mock
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Mock
  private ClothesImageStorage clothesImageStorage;

  @Mock
  private ClothesMapper clothesMapper;

  @InjectMocks
  private ClothesServiceImpl clothesService;

  private UUID ownerId;

  @BeforeEach
  void setUp() {
    ownerId = UUID.randomUUID();
  }

  @Test
  @DisplayName("소유자 ID와 속성을 포함하고 이미지가 있는 의상을 등록한다")
  void 의상_등록_성공() {

    UUID colorId = UUID.randomUUID();
    UUID sizeId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();

    ClothesAttributeDef color = definition(colorId, "색상", List.of("블랙", "크림"));
    ClothesAttributeDef size = definition(sizeId, "사이즈", List.of("S", "L"));

    ClothesImageCommand image = pngImage();
    ClothesCreateCommand command = command(
        List.of(
            new ClothesAttributeCommand(colorId, " 블랙 "),
            new ClothesAttributeCommand(sizeId, "S")
        ),
        image
    );

    ClothesDto expected = new ClothesDto(
        clothesId,
        ownerId,
        "테스트 의상",
        "https://cdn.example.com/clothes/image.png",
        ClothesType.DRESS,
        List.of()
    );

    given(clothesAttributeDefRepository.findAllById(any())).willReturn(List.of(size, color));
    given(clothesImageStorage.upload(image)).willReturn(new StoredImage(
        "clothes/image.png",
        "https://cdn.example.com/clothes/image.png"
    ));
    given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> {
      Clothes clothes = invocation.getArgument(0);
      ReflectionTestUtils.setField(clothes, "id", clothesId);
      return clothes;
    });
    given(clothesMapper.toDto(any(Clothes.class))).willReturn(expected);

    ClothesDto result = clothesService.create(command);

    ArgumentCaptor<Clothes> captor = ArgumentCaptor.forClass(Clothes.class);
    then(clothesRepository).should().save(captor.capture());
    Clothes saved = captor.getValue();

    Assertions.assertAll(
        () -> assertThat(result).isEqualTo(expected),
        () -> assertThat(saved.getOwnerId()).isEqualTo(ownerId),
        () -> assertThat(saved.getName()).isEqualTo("테스트 의상"),
        () -> assertThat(saved.getType()).isEqualTo(ClothesType.DRESS),
        () -> assertThat(saved.getImageUrl()).isEqualTo("https://cdn.example.com/clothes/image.png"),
        () -> assertThat(saved.getAttributes())
            .extracting(attribute -> attribute.getDefinition().getId())
            .containsExactly(colorId, sizeId),
        () -> assertThat(saved.getAttributes())
            .extracting(attribute -> attribute.getValue())
            .containsExactly("블랙", "S")
    );
  }


  private ClothesCreateCommand command(List<ClothesAttributeCommand> attributes, ClothesImageCommand image) {
    return new ClothesCreateCommand(
        ownerId,
        "테스트 의상",
        ClothesType.DRESS,
        attributes,
        image
    );
  }

  private ClothesAttributeDef definition(UUID id, String name, List<String> selectableValues) {
    ClothesAttributeDef definition = ClothesAttributeDef.create(name, selectableValues);
    ReflectionTestUtils.setField(definition, "id", id);
    return definition;
  }

  private ClothesImageCommand pngImage() {
    return new ClothesImageCommand(
        "image.png",
        "image/png",
        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
    );
  }
}
