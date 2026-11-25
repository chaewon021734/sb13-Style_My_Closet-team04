package com.project.sb13ootbsteam04.clothes.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("의상 관리 OpenAPI 문서")
class ClothesOpenApiTest {

  private static final String CREATE_MULTIPART_PATH =
      "$.paths['/api/clothes'].post.requestBody.content['multipart/form-data']";

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  @DisplayName("옷 목록 조회 문서는 첫 페이지의 필수 쿼리와 커서 페이지 응답을 명시한다")
  void 옷_목록_조회_OpenAPI_계약() throws Exception {
    // given
    String operation = "$.paths['/api/clothes'].get";
    String params = operation + ".parameters";
    String responses = operation + ".responses";

    // when & then
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(params + "[?(@.in == 'query')].name")
            .value(containsInAnyOrder("cursor", "idAfter", "limit", "typeEqual", "ownerId")))
        .andExpect(jsonPath(params + "[?(@.required == true)].name")
            .value(containsInAnyOrder("ownerId", "limit")))
        .andExpect(jsonPath(responses + "['200'].content['*/*'].schema['$ref']")
            .value("#/components/schemas/ClothesDtoCursorResponse"))
        .andExpect(jsonPath(responses + "['400'].content['*/*'].schema['$ref']")
            .value("#/components/schemas/ErrorResponse"))
        .andExpect(jsonPath(responses + "['500'].content['*/*'].schema['$ref']")
            .value("#/components/schemas/ErrorResponse"));
  }

  @Test
  @DisplayName("옷 등록을 위한 multipart request는 JSON 필수 파트이고 이미지는 선택 파트이다")
  void 의상_등록_OpenAPI_계약() throws Exception {
    // when & then
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk()) // Open API 명세 조회를 성공적으로 수행했는지 확인
        .andExpect(jsonPath(CREATE_MULTIPART_PATH + ".encoding.request.contentType").value("application/json"))
        .andExpect(jsonPath(CREATE_MULTIPART_PATH + ".schema.required").value(contains("request")))
        .andExpect(jsonPath(CREATE_MULTIPART_PATH + ".schema.required").value(not(hasItem("image"))))
        .andExpect(jsonPath(CREATE_MULTIPART_PATH + ".schema.properties.request['$ref']").value("#/components/schemas/ClothesCreateRequest"))
        .andExpect(jsonPath(CREATE_MULTIPART_PATH + ".schema.properties.image.type").value("string"))
        .andExpect(jsonPath(CREATE_MULTIPART_PATH + ".schema.properties.image.format").value("binary"));
  }

  @Test
  void 의상_수정_OpenAPI_계약() throws Exception {
    // given
    String patch = "$.paths['/api/clothes/{clothesId}'].patch";
    String multipart = patch + ".requestBody.content['multipart/form-data']";

    // when & then
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(multipart + ".encoding.request.contentType").value("application/json"))
        .andExpect(jsonPath(multipart + ".schema.required").value(contains("request")))
        .andExpect(jsonPath(multipart + ".schema.properties.request['$ref']").value("#/components/schemas/ClothesUpdateRequest"))
        .andExpect(jsonPath(multipart + ".schema.properties.image.format").value("binary"))
        .andExpect(jsonPath("$.components.schemas.ClothesUpdateRequest.required").doesNotExist());
  }
}
