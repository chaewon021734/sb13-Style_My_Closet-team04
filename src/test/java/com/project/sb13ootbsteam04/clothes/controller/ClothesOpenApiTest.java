package com.project.sb13ootbsteam04.clothes.controller;

import static org.hamcrest.Matchers.contains;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
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
}
