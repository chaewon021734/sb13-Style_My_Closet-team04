package com.project.sb13ootbsteam04.clothes.controller;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefDto;
import com.project.sb13ootbsteam04.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "의상 속성 정의", description = "의상 속성 정의 관련 API")
public interface ClothesAttributeDefApi {

  @Operation(
      summary = "의상 속성 정의 등록",
      description = "의상 속성 정의 등록 API")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "의상 속성 정의 등록 성공",
          content = @Content(schema = @Schema(implementation = ClothesAttributeDefDto.class))),
      @ApiResponse(
          responseCode = "400",
          description = "의상 속성 정의 등록 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "이미 존재하는 의상 속성 정의 이름",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "서버 처리 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<ClothesAttributeDefDto> create(
      @RequestBody(
          required = true,
          content = @Content(schema = @Schema(implementation = ClothesAttributeDefCreateRequest.class)))
      ClothesAttributeDefCreateRequest request
  );
}
