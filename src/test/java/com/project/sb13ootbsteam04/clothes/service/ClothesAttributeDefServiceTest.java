package com.project.sb13ootbsteam04.clothes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.repository.ClothesAttributeDefRepository;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import com.project.sb13ootbsteam04.clothes.service.impl.ClothesAttributeDefServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("의상 속성 정의 서비스")
@ExtendWith(MockitoExtension.class)
class ClothesAttributeDefServiceTest {

  @Mock
  private ClothesAttributeDefRepository clothesAttributeDefRepository;

  @InjectMocks
  private ClothesAttributeDefServiceImpl clothesAttributeDefService;

  @Test
  @DisplayName("속성 정의 이름과 선택 가능한 값으로 의상 속성 정의를 등록한다")
  void 의상_속성_정의_등록_성공() {
    // given
    UUID definitionId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-28T00:00:00Z");
    String name = "색상";
    List<String> selectableValues = List.of("블랙", "크림");
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand(name, selectableValues);

    given(clothesAttributeDefRepository.save(any(ClothesAttributeDef.class)))
        .willAnswer(invocation -> {
          ClothesAttributeDef saved = invocation.getArgument(0);
          ReflectionTestUtils.setField(saved, "id", definitionId);
          ReflectionTestUtils.setField(saved, "createdAt", createdAt);
          return saved;
        });

    // when
    ClothesAttributeDefResult result = clothesAttributeDefService.create(command);

    // then
    ArgumentCaptor<ClothesAttributeDef> captor = ArgumentCaptor.forClass(ClothesAttributeDef.class);
    then(clothesAttributeDefRepository).should().save(captor.capture());
    ClothesAttributeDef saved = captor.getValue();

    assertAll(
        () -> assertThat(saved.getName()).isEqualTo(name),
        () -> assertThat(saved.getSelectableValues()).containsExactlyElementsOf(selectableValues),
        () -> assertThat(result.id()).isEqualTo(definitionId),
        () -> assertThat(result.name()).isEqualTo(name),
        () -> assertThat(result.selectableValues()).containsExactlyElementsOf(selectableValues),
        () -> assertThat(result.createdAt()).isEqualTo(createdAt)
    );
  }
}
