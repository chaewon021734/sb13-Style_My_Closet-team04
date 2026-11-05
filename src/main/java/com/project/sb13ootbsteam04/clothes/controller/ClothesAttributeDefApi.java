package com.project.sb13ootbsteam04.clothes.controller;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesAttributeDefDto;
import com.project.sb13ootbsteam04.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

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

  @Operation(
      operationId = "getClothesAttributeDef",
      summary = "의상 속성 정의 목록 조회",
      description = "의상 속성 정의 목록 조회 API")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "의상 속성 정의 목록 조회 성공",
          content = @Content(array = @ArraySchema(schema = @Schema(implementation = ClothesAttributeDefDto.class)))),
      @ApiResponse(
          responseCode = "400",
          description = "의상 속성 정의 목록 조회 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "서버 처리 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<List<ClothesAttributeDefDto>> findAll(
      @Parameter(
          description = "정렬 기준",
          required = true,
          schema = @Schema(type = "string", allowableValues = {"createdAt", "name"}))
      @RequestParam(name = "sortBy") String sortBy,
      @Parameter(
          description = "정렬 방향",
          required = true,
          schema = @Schema(type = "string", allowableValues = {"ASCENDING", "DESCENDING"}))
      @RequestParam(name = "sortDirection") String sortDirection,
      @Parameter(description = "속성 정의 이름 검색어")
      @RequestParam(name = "keywordLike", required = false) String keywordLike
  );
}
