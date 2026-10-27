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
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapper;
import com.project.sb13ootbsteam04.clothes.service.ClothesService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
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
    given(clothesService.create(any(ClothesCreateCommand.class))).willReturn(response);

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
