package com.project.sb13ootbsteam04.clothes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDto;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesDto;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.exception.ClothesImageStorageException;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapper;
import com.project.sb13ootbsteam04.clothes.service.ClothesService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

@DisplayName("의상 컨트롤러")
@ExtendWith(MockitoExtension.class)
class ClothesControllerTest {

  @Mock
  private ClothesService clothesService;

  @Mock
  private ClothesMapper clothesMapper;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    mockMvc = MockMvcBuilders.standaloneSetup(new ClothesController(clothesService, clothesMapper))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .build();
  }

  @Test
  @DisplayName("request JSON과 선택 이미지로 등록하고 201 응답을 반환한다")
  void 의상_등록_성공() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID definitionId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();
    ClothesCreateRequest request = new ClothesCreateRequest(
        ownerId,
        "검정 셔츠",
        ClothesType.TOP,
        List.of(new ClothesAttributeDto(definitionId, "블랙"))
    );
    ClothesCreateCommand mappedCommand = new ClothesCreateCommand(
        ownerId,
        "검정 셔츠",
        ClothesType.TOP,
        List.of(new ClothesAttributeCommand(definitionId, "블랙")),
        null
    );
    ClothesResult result = new ClothesResult(
        clothesId,
        ownerId,
        "검정 셔츠",
        "https://cdn.example.com/clothes/image.png",
        ClothesType.TOP,
        List.of()
    );
    ClothesDto response = new ClothesDto(
        clothesId,
        ownerId,
        "검정 셔츠",
        "https://cdn.example.com/clothes/image.png",
        ClothesType.TOP,
        List.of()
    );
    MockMultipartFile requestPart = requestPart(request);
    MockMultipartFile imagePart = pngImagePart();
    given(clothesMapper.toCommand(any(ClothesCreateRequest.class))).willReturn(mappedCommand);
    given(clothesService.create(any(ClothesCreateCommand.class))).willReturn(result);
    given(clothesMapper.toDto(result)).willReturn(response);

    mockMvc.perform(multipart("/api/clothes").file(requestPart).file(imagePart))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(clothesId.toString()))
        .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
        .andExpect(jsonPath("$.name").value("검정 셔츠"))
        .andExpect(jsonPath("$.imageUrl").value("https://cdn.example.com/clothes/image.png"))
        .andExpect(jsonPath("$.type").value("TOP"))
        .andExpect(jsonPath("$.attributes").isArray());

    ArgumentCaptor<ClothesCreateCommand> captor = ArgumentCaptor.forClass(ClothesCreateCommand.class);
    then(clothesService).should().create(captor.capture());

    assertThat(captor.getValue().image()).isNotNull();
    assertThat(captor.getValue().image().contentType()).isEqualTo("image/png");
  }

  @Test
  @DisplayName("이미지 저장소 실패는 Swagger ErrorResponse 구조와 500으로 반환한다")
  void 이미지_저장소_실패() throws Exception {

    UUID ownerId = UUID.randomUUID();
    ClothesCreateRequest request = new ClothesCreateRequest(
        ownerId,
        "검정 셔츠",
        ClothesType.TOP,
        List.of()
    );
    ClothesCreateCommand requestCommand = new ClothesCreateCommand(
        ownerId,
        "검정 셔츠",
        ClothesType.TOP,
        List.of(),
        null
    );
    given(clothesMapper.toCommand(any(ClothesCreateRequest.class))).willReturn(requestCommand);
    given(clothesService.create(any(ClothesCreateCommand.class))).willThrow(
        ClothesImageStorageException.uploadFailed(new IllegalStateException("S3 unavailable"))
    );

    mockMvc.perform(multipart("/api/clothes")
            .file(requestPart(request))
            .file(pngImagePart()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.exceptionName").value("ClothesImageStorageException"))
        .andExpect(jsonPath("$.message").value("의상 이미지를 저장하지 못했습니다"))
        .andExpect(jsonPath("$.details.image").value("이미지 저장소 처리에 실패했습니다"))
        .andExpect(jsonPath("$.details.operation").value("upload"));
  }

  @Test
  @DisplayName("필수 request part가 없으면 Swagger 오류 형식으로 400을 반환한다")
  void request_part_누락() throws Exception {

    MockMultipartFile imagePart = pngImagePart();

    mockMvc.perform(multipart("/api/clothes").file(imagePart))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MissingServletRequestPartException"))
        .andExpect(jsonPath("$.message").value("필수 multipart 항목이 없습니다"))
        .andExpect(jsonPath("$.details.request").value("필수 항목입니다"));

    then(clothesService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("중첩 속성 요청이 유효하지 않으면 400을 반환한다")
  void 중첩_속성_검증_실패() throws Exception {

    ClothesCreateRequest request = new ClothesCreateRequest(
        UUID.randomUUID(),
        "셔츠",
        ClothesType.TOP,
        List.of(new ClothesAttributeDto(null, " "))
    );
    MockMultipartFile requestPart = requestPart(request);

    mockMvc.perform(multipart("/api/clothes").file(requestPart))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
        .andExpect(jsonPath("$.details['attributes[0].definitionId']").exists())
        .andExpect(jsonPath("$.details['attributes[0].value']").exists());

    then(clothesMapper).shouldHaveNoInteractions();
    then(clothesService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("엔티티 컬럼 길이를 초과한 이름과 속성 값은 저장 전에 400으로 거부한다")
  void 문자열_길이_검증_실패() throws Exception {

    ClothesCreateRequest request = new ClothesCreateRequest(
        UUID.randomUUID(),
        "가".repeat(256),
        ClothesType.TOP,
        List.of(new ClothesAttributeDto(UUID.randomUUID(), "나".repeat(101)))
    );

    mockMvc.perform(multipart("/api/clothes").file(requestPart(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
        .andExpect(jsonPath("$.details.name").value("의상 이름은 255자까지만 입력 가능합니다."))
        .andExpect(jsonPath("$.details['attributes[0].value']")
            .value("속성 값은 100자까지만 입력 가능합니다"));

    then(clothesMapper).shouldHaveNoInteractions();
    then(clothesService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("도메인 필수값인 ownerId가 없으면 400을 반환한다")
  void 소유자_ID_누락() throws Exception {

    ClothesCreateRequest request = new ClothesCreateRequest(null, "셔츠", ClothesType.TOP, List.of());

    mockMvc.perform(multipart("/api/clothes").file(requestPart(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"))
        .andExpect(jsonPath("$.details.ownerId").value("의상 소유자 ID는 필수입니다"));

    then(clothesMapper).shouldHaveNoInteractions();
    then(clothesService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("이미지 시그니처와 콘텐츠 타입이 다르면 400을 반환한다")
  void 이미지_검증_실패() throws Exception {

    UUID ownerId = UUID.randomUUID();
    ClothesCreateRequest request = new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP, List.of());
    MockMultipartFile requestPart = requestPart(request);
    MockMultipartFile imagePart = new MockMultipartFile("image", "fake.png", MediaType.IMAGE_PNG_VALUE, "not-png".getBytes());

    mockMvc.perform(multipart("/api/clothes").file(requestPart).file(imagePart))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("InvalidClothesImageException"))
        .andExpect(jsonPath("$.details.image").exists());

    then(clothesService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("request part의 JSON 문법이 잘못되면 400을 반환한다")
  void request_JSON_파싱_실패() throws Exception {

    MockMultipartFile requestPart = new MockMultipartFile(
        "request",
        "request.json",
        MediaType.APPLICATION_JSON_VALUE,
        "{invalid-json".getBytes()
    );

    mockMvc.perform(multipart("/api/clothes").file(requestPart))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("HttpMessageNotReadableException"))
        .andExpect(jsonPath("$.details.request").exists());

    then(clothesMapper).shouldHaveNoInteractions();
    then(clothesService).shouldHaveNoInteractions();
  }

  private MockMultipartFile requestPart(ClothesCreateRequest request) throws Exception {
    return new MockMultipartFile(
        "request",
        "request.json",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(request)
    );
  }

  private MockMultipartFile pngImagePart() {
    return new MockMultipartFile(
        "image",
        "image.png",
        MediaType.IMAGE_PNG_VALUE,
        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
    );
  }
}
