package com.project.sb13ootbsteam04.clothes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDto;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesCreateRequest;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.clothes.exception.ClothesAccessException;
import com.project.sb13ootbsteam04.clothes.exception.ClothesImageStorageException;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapper;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesMapperImpl;
import com.project.sb13ootbsteam04.clothes.service.ClothesService;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesAttributeResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCreateCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesCursorResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesResult;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesSearchCommand;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesUpdateCommand;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

@DisplayName("의상 컨트롤러")
@ExtendWith(MockitoExtension.class)
class ClothesControllerTest {

  @Mock
  private ClothesService clothesService;

  @Spy
  private ClothesMapper clothesMapper = new ClothesMapperImpl();

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
    ClothesResult result = new ClothesResult(
        clothesId,
        ownerId,
        "검정 셔츠",
        "https://cdn.example.com/clothes/image.png",
        ClothesType.TOP,
        List.of()
    );
    MockMultipartFile requestPart = requestPart(request);
    MockMultipartFile imagePart = pngImagePart();
    given(clothesService.create(any(ClothesCreateCommand.class))).willReturn(result);

    mockMvc.perform(multipart("/api/clothes").file(requestPart).file(imagePart).principal(login(ownerId)))
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
  @DisplayName("다른 사용자의 옷장에 의상을 등록하면 403을 반환한다")
  void 다른_소유자로_등록_거부() throws Exception {

    UUID ownerId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    ClothesCreateRequest request = new ClothesCreateRequest(ownerId, "셔츠", ClothesType.TOP, List.of());

    mockMvc.perform(multipart("/api/clothes").file(requestPart(request)).principal(login(actorId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("의상 소유자만 이 작업을 수행할 수 있습니다"));

    then(clothesService).shouldHaveNoInteractions();
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
    given(clothesService.create(any(ClothesCreateCommand.class))).willThrow(
        ClothesImageStorageException.uploadFailed(new IllegalStateException("S3 unavailable"))
    );

    mockMvc.perform(multipart("/api/clothes")
            .file(requestPart(request))
            .file(pngImagePart())
            .principal(login(ownerId)))
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

    mockMvc.perform(multipart("/api/clothes").file(requestPart).file(imagePart).principal(login(ownerId)))
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

  @Test
  @DisplayName("첫 페이지는 ownerId와 limit만 받아 의상과 다음 페이지 커서를 반환한다")
  void 옷_목록_첫_페이지_조회() throws Exception {
    // given
    UUID ownerId = UUID.randomUUID(), clothesId = UUID.randomUUID(), definitionId = UUID.randomUUID();
    String nextCursor = "2026-11-19T02:00:00Z";
    ClothesAttributeResult attribute = new ClothesAttributeResult(definitionId, "색상", List.of("블랙", "크림"), "크림");
    ClothesResult clothes = new ClothesResult(clothesId, ownerId, "재킷", "https://cdn.example.com/jacket.png", ClothesType.OUTER, List.of(attribute));
    ClothesSearchCommand command = new ClothesSearchCommand(null, null, 1, null, ownerId);
    given(clothesService.search(command)).willReturn(new ClothesCursorResult(List.of(clothes), nextCursor, clothesId, true, 2, "createdAt", "DESCENDING"));

    // when & then: 정적 클라이언트가 사용하는 JSON 키와 커서 값을 검증한다.
    mockMvc.perform(get("/api/clothes").param("ownerId", ownerId.toString()).param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(clothesId.toString()))
        .andExpect(jsonPath("$.data[0].ownerId").value(ownerId.toString()))
        .andExpect(jsonPath("$.data[0].name").value("재킷"))
        .andExpect(jsonPath("$.data[0].imageUrl").value(clothes.imageUrl()))
        .andExpect(jsonPath("$.data[0].type").value("OUTER"))
        .andExpect(jsonPath("$.data[0].attributes[0].definitionId").value(definitionId.toString()))
        .andExpect(jsonPath("$.data[0].attributes[0].definitionName").value("색상"))
        .andExpect(jsonPath("$.data[0].attributes[0].selectableValues").value(contains("블랙", "크림")))
        .andExpect(jsonPath("$.data[0].attributes[0].value").value("크림"))
        .andExpect(jsonPath("$.nextCursor").value(nextCursor))
        .andExpect(jsonPath("$.nextIdAfter").value(clothesId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
    then(clothesService).should().search(command);
  }

  @Test
  @DisplayName("다음 페이지는 커서 쌍과 의상 타입을 서비스에 전달하고 마지막 페이지를 반환한다")
  void 옷_목록_다음_페이지() throws Exception {
    // given
    UUID ownerId = UUID.randomUUID(), idAfter = UUID.randomUUID();
    String cursor = "2026-11-19T02:00:00Z";
    ClothesSearchCommand command = new ClothesSearchCommand(cursor, idAfter, 1, ClothesType.ETC, ownerId);
    ClothesResult last = new ClothesResult(UUID.randomUUID(), ownerId, "머리끈", null, ClothesType.ETC, List.of());
    given(clothesService.search(command)).willReturn(new ClothesCursorResult(List.of(last), null, null, false, 2, "createdAt", "DESCENDING"));

    // when & then
    mockMvc.perform(get("/api/clothes").param("ownerId", ownerId.toString()).param("limit", "1")
            .param("cursor", cursor).param("idAfter", idAfter.toString()).param("typeEqual", "ETC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(last.id().toString()))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.nextIdAfter").value(nullValue()));
    then(clothesService).should().search(command);
  }

  @Test
  @DisplayName("서비스가 잘못된 커서를 거부하면 공통 오류 형식으로 400을 반환한다")
  void 옷_목록_잘못된_커서() throws Exception {
    // given
    UUID ownerId = UUID.randomUUID(), idAfter = UUID.randomUUID();
    ClothesSearchCommand command = new ClothesSearchCommand("invalid", idAfter, 10, null, ownerId);
    given(clothesService.search(command)).willThrow(new IllegalArgumentException("cursor 형식 오류"));

    // when & then
    mockMvc.perform(get("/api/clothes").param("ownerId", ownerId.toString()).param("limit", "10")
            .param("cursor", "invalid").param("idAfter", idAfter.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("IllegalArgumentException"))
        .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다"))
        .andExpect(jsonPath("$.details").isMap());
    then(clothesService).should().search(command);
  }

  @Test
  @DisplayName("이미지를 첨부해 의상을 수정하면 이미지와 속성을 전달한다")
  void 이미지_첨부_의상을_수정_성공() throws Exception {
    // given: 정적 프런트가 보내는 JSON 필드명을 그대로 사용한다.
    UUID clothesId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID definitionId = UUID.randomUUID();
    Map<String, Object> request = Map.of(
        "name", "수정된 가방",
        "type", "BAG",
        "attributes", List.of(Map.of("definitionId", definitionId, "value", "핑크"))
    );

    MockMultipartFile image = pngImagePart();
    String imageUrl = "https://cdn.example.com/clothes/bag.png";
    ClothesResult result = new ClothesResult(clothesId, ownerId, "수정된 가방", imageUrl, ClothesType.BAG,
        List.of(new ClothesAttributeResult(definitionId, "색상", List.of("블랙", "핑크"), "핑크")));
    given(clothesService.update(eq(ownerId), any(ClothesUpdateCommand.class))).willReturn(result);

    // when & then: 실제 매퍼를 거쳐 프런트가 읽는 중첩 필드까지 검증한다.
    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{clothesId}", clothesId)
            .file(requestPart(request)).file(image).principal(login(ownerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(clothesId.toString()))
        .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
        .andExpect(jsonPath("$.name").value("수정된 가방"))
        .andExpect(jsonPath("$.imageUrl").value(imageUrl))
        .andExpect(jsonPath("$.type").value("BAG"))
        .andExpect(jsonPath("$.attributes[0].definitionId").value(definitionId.toString()))
        .andExpect(jsonPath("$.attributes[0].definitionName").value("색상"))
        .andExpect(jsonPath("$.attributes[0].selectableValues").value(contains("블랙", "핑크")))
        .andExpect(jsonPath("$.attributes[0].value").value("핑크"));

    ArgumentCaptor<ClothesUpdateCommand> captor = ArgumentCaptor.forClass(ClothesUpdateCommand.class);
    then(clothesService).should().update(eq(ownerId), captor.capture());
    ClothesUpdateCommand command = captor.getValue();
    assertThat(command.clothesId()).isEqualTo(clothesId);
    assertThat(command.name()).isEqualTo("수정된 가방");
    assertThat(command.type()).isEqualTo(ClothesType.BAG);
    assertThat(command.attributes()).containsExactly(new ClothesAttributeCommand(definitionId, "핑크"));
    assertThat(command.image()).isNotNull();
    assertThat(command.image().contentType()).isEqualTo(image.getContentType());
    assertThat(command.image().content()).containsExactly(image.getBytes());
  }

  @Test
  @DisplayName("이미지 없이 이름만 수정하면 생략한 필드는 null로 전달한다")
  void 이미지_미첨부_이름만_수정() throws Exception {
    // given
    UUID clothesId = UUID.randomUUID();
    String existingUrl = "https://cdn.example.com/clothes/original.png";
    ClothesUpdateCommand expected = new ClothesUpdateCommand(clothesId, "새 이름", null, null, null);
    ClothesResult result = new ClothesResult(clothesId, UUID.randomUUID(), "새 이름", existingUrl, ClothesType.BAG, List.of());
    given(clothesService.update(result.ownerId(), expected)).willReturn(result);

    // when & then: 이미지 파트를 보내지 않아도 서비스가 반환한 기존 URL을 응답한다.
    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{clothesId}", clothesId)
            .file(requestPart(Map.of("name", "새 이름"))).principal(login(result.ownerId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageUrl").value(existingUrl));
    then(clothesService).should().update(result.ownerId(), expected);
  }

  @Test
  @DisplayName("의상 수정 시 request part를 누락하면 400을 반환한다")
  void 의상_수정_request_part_누락() throws Exception {
    // given
    UUID clothesId = UUID.randomUUID();

    // when & then
    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{clothesId}", clothesId).file(pngImagePart()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.request").value("필수 항목입니다"));
    then(clothesService).shouldHaveNoInteractions();
  }


  @Test
  @DisplayName("로그인 없이 옷을 수정하면 401이고 서비스가 호출되지 않는다")
  void 미인증_수정_거부() throws Exception {
    // when & then
    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{id}", UUID.randomUUID())
            .file(requestPart(Map.of("name", "새 이름"))))
        .andExpect(status().isUnauthorized());
    then(clothesService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("의상 소유권 오류는 403으로 반환한다")
  void 소유권_오류_응답() throws Exception {
    // given
    UUID actorId = UUID.randomUUID();
    given(clothesService.update(eq(actorId), any())).willThrow(ClothesAccessException.forbidden());

    // when & then
    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/clothes/{id}", UUID.randomUUID())
            .file(requestPart(Map.of("name", "새 이름"))).principal(login(actorId)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("인증된 사용자의 ID와 의상 ID로 삭제를 요청하고 빈 204 응답을 반환한다")
  void 의상_삭제_성공() throws Exception {
    // given
    UUID clothesId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    // 인증 필터가 전달하는 주체의 UUID가 서비스까지 전달되는지 확인한다.
    Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(actorId.toString()).build();

    // when & then
    mockMvc.perform(delete("/api/clothes/{clothesId}", clothesId)
            .principal(new JwtAuthenticationToken(jwt, List.of())))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    then(clothesService).should().delete(actorId, clothesId);
    then(clothesService).shouldHaveNoMoreInteractions();
  }

  private JwtAuthenticationToken login(UUID actorId) {
    Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(actorId.toString()).build();
    return new JwtAuthenticationToken(jwt, List.of());
  }

  private MockMultipartFile requestPart(Object request) throws Exception {
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
