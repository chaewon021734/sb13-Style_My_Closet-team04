package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.project.sb13ootbsteam04.clothes.exception.ClothesOwnerNotFoundException;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeException;
import com.project.sb13ootbsteam04.clothes.exception.InvalidClothesAttributeValueException;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapper;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.service.impl.ClothesServiceImpl;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.StoredImage;
import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionCallback;

@DisplayName("의상 서비스")
@ExtendWith(MockitoExtension.class)
class ClothesServiceTest {

  @Mock
  private ClothesRepository clothesRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

  @Mock
  private ClothesImageStorage clothesImageStorage;

  @Mock
  private ClothesMapper clothesMapper;

  @Mock
  private TransactionOperations transactionOperations;

  @InjectMocks
  private ClothesServiceImpl clothesService;

  private UUID ownerId;
  private User owner;

  @BeforeEach
  void setUp() {
    ownerId = UUID.randomUUID();
    owner = new User("소유자", "owner@example.com", "encoded-password");
    ReflectionTestUtils.setField(owner, "id", ownerId);
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

    ClothesResult expected = new ClothesResult(
        clothesId,
        ownerId,
        "테스트 의상",
        "https://cdn.example.com/clothes/image.png",
        ClothesType.DRESS,
        List.of()
    );

    givenOwnerExists();
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
    given(clothesMapper.toResult(any(Clothes.class))).willReturn(expected);
    givenTransactionExecution();

    ClothesResult result = clothesService.create(command);

    ArgumentCaptor<Clothes> captor = ArgumentCaptor.forClass(Clothes.class);
    then(clothesRepository).should().save(captor.capture());
    Clothes saved = captor.getValue();
    InOrder persistenceOrder = inOrder(
        clothesImageStorage,
        transactionOperations,
        clothesRepository
    );

    assertAll(
        () -> assertThat(result).isEqualTo(expected),
        () -> assertThat(saved.getOwner()).isSameAs(owner),
        () -> assertThat(saved.getName()).isEqualTo("테스트 의상"),
        () -> assertThat(saved.getType()).isEqualTo(ClothesType.DRESS),
        () -> assertThat(saved.getImageUrl()).isEqualTo("https://cdn.example.com/clothes/image.png"),
        () -> assertThat(saved.getAttributes())
            .extracting(attribute -> attribute.getDefinition().getId())
            .containsExactly(colorId, sizeId),
        () -> assertThat(saved.getAttributes())
            .extracting(attribute -> attribute.getValue())
            .containsExactly("블랙", "S"),
        () -> {
          persistenceOrder.verify(clothesImageStorage).upload(image);
          persistenceOrder.verify(transactionOperations).execute(any());
          persistenceOrder.verify(clothesRepository).save(any(Clothes.class));
        }
    );
  }

  @Test
  @DisplayName("같은 속성 정의가 중복되면 구조화된 도메인 예외를 발생시킨다")
  void 중복_속성_정의_거부() {

    UUID definitionId = UUID.randomUUID();
    ClothesCreateCommand command = command(
        List.of(
            new ClothesAttributeCommand(definitionId, "블랙"),
            new ClothesAttributeCommand(definitionId, "크림")
        ),
        null
    );
    givenOwnerExists();

    assertThatThrownBy(() -> clothesService.create(command))
        .isInstanceOfSatisfying(DuplicateClothesAttributeException.class, exception -> {
          assertAll(
              () -> assertThat(exception.getErrorCode())
                  .isEqualTo(ErrorCode.CLOTHES_ATTRIBUTE_DUPLICATED),
              () -> assertThat(exception.getDetails())
                  .containsEntry("definitionId", definitionId.toString())
          );
        });

    then(clothesRepository).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("속성 정의를 찾을 수 없으면 누락된 해당 ID를 포함한 도메인 예외를 발생시킨다")
  void 존재하지_않는_속성_정의_거부() {

    UUID definitionId = UUID.randomUUID();
    ClothesCreateCommand command = command(
        List.of(new ClothesAttributeCommand(definitionId, "블랙")),
        null
    );
    givenOwnerExists();
    given(clothesAttributeDefRepository.findAllById(any())).willReturn(List.of());

    assertThatThrownBy(() -> clothesService.create(command))
        .isInstanceOfSatisfying(
            ClothesAttributeDefinitionNotFoundException.class,
            exception -> assertAll(
                () -> assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND),
                () -> assertThat(exception.getDetails())
                    .containsEntry("definitionIds", definitionId.toString())
            )
        );

    then(clothesRepository).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("존재하지 않는 소유자 ID이면 의상을 등록하지 않는다")
  void 존재하지_않는_소유자_거부() {
    ClothesCreateCommand command = command(List.of(), null);
    given(userRepository.findById(ownerId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> clothesService.create(command))
        .isInstanceOfSatisfying(ClothesOwnerNotFoundException.class, exception ->
            assertAll(
                () -> assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CLOTHES_OWNER_NOT_FOUND),
                () -> assertThat(exception.getDetails())
                    .containsEntry("ownerId", ownerId.toString())
            ));

    assertAll(
        () -> then(clothesAttributeDefRepository).shouldHaveNoInteractions(),
        () -> then(clothesImageStorage).shouldHaveNoInteractions(),
        () -> then(clothesRepository).shouldHaveNoInteractions()
    );
  }

  @Test
  @DisplayName("이미지가 없어도 의상을 등록할 수 있고 이때 이미지 저장소는 호출하지 않는다")
  void 이미지_없이_의상_등록_성공() {

    ClothesCreateCommand command = command(List.of(), null);
    ClothesResult expected = new ClothesResult(
        UUID.randomUUID(),
        ownerId,
        "테스트 의상",
        null,
        ClothesType.DRESS,
        List.of()
    );
    givenOwnerExists();
    given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(clothesMapper.toResult(any(Clothes.class))).willReturn(expected);
    givenTransactionExecution();

    ClothesResult result = clothesService.create(command);

    assertAll(
        () -> assertThat(result).isEqualTo(expected),
        () -> then(clothesImageStorage).shouldHaveNoInteractions(),
        () -> then(clothesRepository).should().save(any(Clothes.class))
    );
  }

  @Test
  @DisplayName("허용되지 않은 속성 값이면 이미지를 업로드하거나 의상을 저장하지 않는다")
  void 허용되지_않은_속성값_거부() {

    UUID definitionId = UUID.randomUUID();
    ClothesAttributeDef definition = definition(definitionId, "색상", List.of("블랙"));
    ClothesCreateCommand command = command(
        List.of(new ClothesAttributeCommand(definitionId, "크림")),
        pngImage()
    );
    givenOwnerExists();
    given(clothesAttributeDefRepository.findAllById(any())).willReturn(List.of(definition));

    assertThatThrownBy(() -> clothesService.create(command))
        .isInstanceOfSatisfying(InvalidClothesAttributeValueException.class, exception -> {
          assertAll(
              () -> assertThat(exception.getErrorCode())
                  .isEqualTo(ErrorCode.CLOTHES_ATTRIBUTE_VALUE_INVALID),
              () -> assertThat(exception.getDetails())
                  .containsEntry("definitionId", definitionId.toString())
                  .containsEntry("value", "크림")
          );
        });

    assertAll(
        () -> then(clothesImageStorage).shouldHaveNoInteractions(),
        () -> then(clothesRepository).shouldHaveNoInteractions()
    );
  }

  @Test
  @DisplayName("이미지 업로드 후 의상 저장에 실패하면 업로드한 객체를 삭제한다")
  void 의상_저장_실패시_업로드한_이미지_정리() {

    ClothesImageCommand image = pngImage();
    ClothesCreateCommand command = command(List.of(), image);
    StoredImage storedImage = new StoredImage(
        "clothes/image.png",
        "https://cdn.example.com/clothes/image.png"
    );
    RuntimeException saveFailure = new RuntimeException("의상 저장 실패");
    givenOwnerExists();
    given(clothesImageStorage.upload(image)).willReturn(storedImage);
    given(clothesRepository.save(any(Clothes.class))).willThrow(saveFailure);
    givenTransactionExecution();

    assertThatThrownBy(() -> clothesService.create(command))
        .isSameAs(saveFailure);

    assertAll(
        () -> then(clothesImageStorage).should().upload(image),
        () -> then(clothesImageStorage).should().delete(storedImage.objectKey()),
        () -> then(clothesMapper).shouldHaveNoInteractions()
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

  private void givenOwnerExists() {
    given(userRepository.findById(ownerId)).willReturn(Optional.of(owner));
  }

  private void givenTransactionExecution() {
    given(transactionOperations.execute(any())).willAnswer(invocation -> {
      TransactionCallback<?> callback = invocation.getArgument(0);
      return callback.doInTransaction((TransactionStatus) null);
    });
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
