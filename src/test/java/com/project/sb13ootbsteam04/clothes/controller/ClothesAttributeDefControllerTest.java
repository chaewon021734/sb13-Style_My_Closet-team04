package com.project.sb13ootbsteam04.clothes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.same;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefDto;
import com.project.sb13ootbsteam04.clothes.exception.DuplicateClothesAttributeDefNameException;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesAttributeDefMapper;
import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeDefResult;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

@DisplayName("의상 속성 정의 컨트롤러")
@ExtendWith(MockitoExtension.class)
class ClothesAttributeDefControllerTest {

  @Mock
  private ClothesAttributeDefService clothesAttributeDefService;

  @Mock
  private ClothesAttributeDefMapper clothesAttributeDefMapper;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    mockMvc = MockMvcBuilders.standaloneSetup(new ClothesAttributeDefController(clothesAttributeDefService, clothesAttributeDefMapper))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .build();
  }

  @Test
  @DisplayName("JSON 요청으로 속성 정의를 등록하고 Swagger 응답 구조와 201을 반환한다")
  void 의상_속성_정의_등록_성공() throws Exception {
    // given
    UUID definitionId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-28T00:00:00Z");
    List<String> selectableValues = List.of("블랙", "크림");
    ClothesAttributeDefCreateRequest request = new ClothesAttributeDefCreateRequest("색상", selectableValues);
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand("색상", selectableValues);
    ClothesAttributeDefResult result = new ClothesAttributeDefResult(
        definitionId,
        "색상",
        selectableValues,
        createdAt
    );
    ClothesAttributeDefDto response = new ClothesAttributeDefDto(
        definitionId,
        "색상",
        selectableValues,
        createdAt
    );

    given(clothesAttributeDefMapper.toCommand(any(ClothesAttributeDefCreateRequest.class))).willReturn(command);
    given(clothesAttributeDefService.create(command)).willReturn(result);
    given(clothesAttributeDefMapper.toDto(result)).willReturn(response);

    // when & then
    mockMvc.perform(post("/api/clothes/attribute-defs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(definitionId.toString()))
        .andExpect(jsonPath("$.name").value("색상"))
        .andExpect(jsonPath("$.selectableValues[0]").value("블랙"))
        .andExpect(jsonPath("$.selectableValues[1]").value("크림"))
        .andExpect(jsonPath("$.createdAt").value("2026-10-28T00:00:00Z"));

    ArgumentCaptor<ClothesAttributeDefCreateRequest> captor =
        ArgumentCaptor.forClass(ClothesAttributeDefCreateRequest.class);
    then(clothesAttributeDefMapper).should().toCommand(captor.capture());
    then(clothesAttributeDefService).should().create(same(command));
    then(clothesAttributeDefMapper).should().toDto(same(result));
    assertThat(captor.getValue()).isEqualTo(request);
  }

  @Test
  @DisplayName("이름과 선택 가능한 값이 유효하지 않으면 400을 반환한다")
  void 의상_속성_정의_요청_검증_실패() throws Exception {
    // given
    ClothesAttributeDefCreateRequest request = new ClothesAttributeDefCreateRequest(" ", List.of());

    // when & then
    mockMvc.perform(post("/api/clothes/attribute-defs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
        .andExpect(jsonPath("$.details.name").value("속성 정의 이름은 필수입니다"))
        .andExpect(jsonPath("$.details.selectableValues")
            .value("선택 가능한 값은 최소 하나 이상 필요합니다"));

    then(clothesAttributeDefMapper).shouldHaveNoInteractions();
    then(clothesAttributeDefService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("이름과 선택 가능한 값이 최대 길이를 초과하면 400을 반환한다")
  void 의상_속성_정의_요청_길이_검증_실패() throws Exception {
    // given
    ClothesAttributeDefCreateRequest request = new ClothesAttributeDefCreateRequest(
        "가".repeat(256),
        List.of("나".repeat(101))
    );

    // when & then
    mockMvc.perform(post("/api/clothes/attribute-defs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
        .andExpect(jsonPath("$.details.name").value("속성 정의 이름은 255자까지만 입력 가능합니다"))
        .andExpect(jsonPath("$.details['selectableValues[0]']")
            .value("선택 가능한 값은 100자까지만 입력 가능합니다"));

    then(clothesAttributeDefMapper).shouldHaveNoInteractions();
    then(clothesAttributeDefService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("같은 이름이 이미 존재하면 Swagger 오류 구조와 409를 반환한다")
  void 의상_속성_정의_이름_중복() throws Exception {
    // given
    ClothesAttributeDefCreateRequest request = new ClothesAttributeDefCreateRequest("색상", List.of("블랙", "크림"));
    ClothesAttributeDefCreateCommand command = new ClothesAttributeDefCreateCommand("색상", List.of("블랙", "크림"));

    given(clothesAttributeDefMapper.toCommand(any(ClothesAttributeDefCreateRequest.class))).willReturn(command);
    given(clothesAttributeDefService.create(command)).willThrow(new DuplicateClothesAttributeDefNameException("색상"));

    // when & then
    mockMvc.perform(post("/api/clothes/attribute-defs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.exceptionName").value("DuplicateClothesAttributeDefNameException"))
        .andExpect(jsonPath("$.message").value("이미 존재하는 의상 속성 정의 이름입니다"))
        .andExpect(jsonPath("$.details.name").value("색상"));

    then(clothesAttributeDefMapper).should().toCommand(any(ClothesAttributeDefCreateRequest.class));
    then(clothesAttributeDefService).should().create(same(command));
    then(clothesAttributeDefMapper).shouldHaveNoMoreInteractions();
  }
}
