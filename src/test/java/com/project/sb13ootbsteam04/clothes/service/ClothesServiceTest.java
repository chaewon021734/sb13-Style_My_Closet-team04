package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.exception.ClothesAttributeDefinitionNotFoundException;
import com.project.sb13ootbsteam04.clothes.exception.ClothesNotFoundException;
import com.project.sb13ootbsteam04.clothes.exception.ClothesOwnerNotFoundException;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeException;
import com.project.sb13ootbsteam04.clothes.exception.InvalidClothesAttributeValueException;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapper;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesUpdateCommand;
import com.project.sb13ootbsteam04.clothes.service.impl.ClothesServiceImpl;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.StoredImage;
import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

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

  // 등록과 수정 모두 실제 MapStruct 구현으로 결과를 매핑한다.
  @Spy
  private ClothesMapper clothesMapper = Mappers.getMapper(ClothesMapper.class);

  // DB 없이 콜백을 실행하면서 업로드와 트랜잭션 진입 순서를 검증한다.
  @Spy
  private TransactionOperations transactionOperations = TransactionOperations.withoutTransaction();

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
        List.of(new ClothesAttributeResult(colorId, "색상", List.of("블랙", "크림"), "블랙"),
            new ClothesAttributeResult(sizeId, "사이즈", List.of("S", "L"), "S"))
    );

    givenOwnerExists();
    given(clothesAttributeDefRepository.findAllById(any())).willReturn(List.of(size, color));
    given(clothesImageStorage.upload(image)).willReturn(new StoredImage(
        "clothes/image.png",
        "https://cdn.example.com/clothes/image.png"
    ));
    givenSavedClothesId(clothesId);

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
    givenSavedClothesId(expected.id());

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

    assertThatThrownBy(() -> clothesService.create(command))
        .isSameAs(saveFailure);

    assertAll(
        () -> then(clothesImageStorage).should().upload(image),
        () -> then(clothesImageStorage).should().delete(storedImage.objectKey()),
        () -> then(clothesMapper).shouldHaveNoInteractions()
    );
  }

  @Test
  @DisplayName("수정 화면에서 이름만 바꾸면 기존 속성값과 이미지 URL을 유지한다")
  void 기존_속성값을_그대로_전송하면_속성과_이미지를_유지한다() {
    // given
    UUID sizeId = UUID.randomUUID();
    List<String> sizes = List.of("S", "M");
    ClothesAttributeDef size = definition(sizeId, "사이즈", sizes);
    Clothes clothes = existingClothesWithSize(size);
    given(clothesAttributeDefRepository.findAllById(Set.of(sizeId))).willReturn(List.of(size));
    String originalUrl = clothes.getImageUrl();
    // 수정 화면은 변경하지 않은 선택값도 다시 보낸다.
    ClothesUpdateCommand command = new ClothesUpdateCommand(clothes.getId(), "수정된 스카프",
        ClothesType.SCARF, List.of(new ClothesAttributeCommand(sizeId, "S")), null);

    // when
    ClothesResult result = clothesService.update(command);

    // then
    assertAll(
        () -> assertThat(clothes.getImageUrl()).isEqualTo(originalUrl),
        () -> assertThat(clothes.getAttributes()).extracting(ClothesAttribute::getValue).containsExactly("S"),
        () -> assertThat(result).isEqualTo(new ClothesResult(command.clothesId(), ownerId, "수정된 스카프",
            originalUrl, ClothesType.SCARF, List.of(new ClothesAttributeResult(sizeId, "사이즈", sizes, "S"))))
    );
    then(clothesImageStorage).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("PATCH 요청에 이름만 포함하면 기존 종류·속성·이미지를 보존한다")
  void 수정_필드를_생략하면_기존_값을_유지한다() {
    // given
    ClothesAttributeDef size = definition(UUID.randomUUID(), "사이즈", List.of("S", "M"));
    Clothes clothes = existingClothesWithSize(size);
    ClothesUpdateCommand command = new ClothesUpdateCommand(clothes.getId(), "새 이름", null, null, null);
    ClothesResult expected = new ClothesResult(clothes.getId(), ownerId, "새 이름", clothes.getImageUrl(), ClothesType.SCARF,
        List.of(new ClothesAttributeResult(size.getId(), "사이즈", size.getSelectableValues(), "S")));

    // when
    ClothesResult result = clothesService.update(command);

    // then
    assertThat(result).isEqualTo(expected);
    then(clothesImageStorage).shouldHaveNoInteractions();
  }

  @Test
  void 수정할_옷이_없으면_옷_ID를_반환하고_이미지는_업로드하지_않는다() {
    // given
    UUID id = UUID.randomUUID();
    ClothesUpdateCommand command = new ClothesUpdateCommand(id, null, null, null, pngImage());
    given(clothesRepository.findByIdForUpdate(id)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> clothesService.update(command))
        .isInstanceOfSatisfying(ClothesNotFoundException.class, exception -> assertAll(
            () -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_NOT_FOUND),
            () -> assertThat(exception.getDetails()).containsEntry("clothesId", id.toString())));
    then(clothesImageStorage).shouldHaveNoInteractions();
  }

  private Clothes existingClothesWithSize(ClothesAttributeDef size) {
    Clothes clothes = Clothes.create(owner, "기존 스카프", ClothesType.SCARF, "https://cdn.example.com/clothes/old.png");
    UUID id = UUID.randomUUID();
    ReflectionTestUtils.setField(clothes, "id", id);
    ClothesAttribute.create(clothes, size, "S");
    given(clothesRepository.findByIdForUpdate(id)).willReturn(Optional.of(clothes));
    return clothes;
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

  private void givenSavedClothesId(UUID id) {
    // 저장소가 발급하는 ID만 재현한다.
    given(clothesRepository.save(any(Clothes.class))).willAnswer(invocation -> {
      Clothes clothes = invocation.getArgument(0);
      ReflectionTestUtils.setField(clothes, "id", id);
      return clothes;
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
