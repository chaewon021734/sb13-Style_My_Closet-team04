package com.project.sb13ootbsteam04.clothes.controller;

import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesCreateRequest;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesDto;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesDtoCursorResponse;
import com.project.sb13ootbsteam04.clothes.controller.dto.ClothesUpdateRequest;
import com.project.sb13ootbsteam04.clothes.entity.ClothesType;
import com.project.sb13ootbsteam04.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "의상 관리", description = "의상 관련 API")
public interface ClothesApi {

  @Operation(
      summary = "옷 등록",
      description = "새 옷을 등록합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "옷 등록 성공",
          content = @Content(schema = @Schema(implementation = ClothesDto.class))),
      @ApiResponse(
          responseCode = "400",
          description = "옷 등록 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "소유자 또는 의상 속성 정의를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "413",
          description = "이미지 크기 제한 초과",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "415",
          description = "지원하지 않는 이미지 형식",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "이미지 저장소 또는 서버 처리 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<ClothesDto> create(
      @Parameter(
          description = "옷 등록 요청",
          required = true,
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ClothesCreateRequest.class)
          )
      )
      ClothesCreateRequest request,
      MultipartFile image
  );

  @Operation(
      summary = "옷 목록 조회",
      description = "소유자의 옷을 등록일 내림차순으로 조회합니다. 다음 페이지는 직전 응답의 nextCursor와 nextIdAfter를 함께 전달합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "옷 목록 조회 성공 (조회 조건에 맞는 의상이 없으면 빈 목록을 반환합니다.)",
          content = @Content(schema = @Schema(implementation = ClothesDtoCursorResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "필수 파라미터 누락, 잘못된 UUID·의상 종류·커서, 커서 쌍 불일치 또는 limit 범위 초과",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "서버 처리 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<ClothesDtoCursorResponse> search(
      @Parameter(description = "직전 응답의 nextCursor") String cursor,
      @Parameter(description = "직전 응답의 nextIdAfter") UUID idAfter,
      @Parameter(description = "한 페이지에 조회할 의상 수 (서비스 허용 범위 1~100)") int limit,
      @Parameter(description = "필터링할 의상 종류") ClothesType typeEqual,
      @Parameter(description = "옷 소유자 ID") UUID ownerId
  );

  @Operation(
      summary = "옷 수정",
      description = "로그인한 사용자가 소유한 옷을 수정합니다. 이미지를 생략하면 기존 이미지를 유지합니다.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "옷 수정 성공",
          content = @Content(schema = @Schema(implementation = ClothesDto.class))),
      @ApiResponse(
          responseCode = "400",
          description = "옷 수정 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "유효한 사용자 인증이 필요합니다. 보안 필터에서 거부하면 응답 본문이 없을 수 있습니다.",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "수정 권한이 없습니다. 타인 의상은 오류 본문을 반환하며 보안 필터에서 거부하면 본문이 없을 수 있습니다.",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "의상 또는 의상 속성 정의를 찾을 수 없음",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "413",
          description = "이미지 크기 제한 초과",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "415",
          description = "지원하지 않는 이미지 형식",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "500",
          description = "이미지 저장소 또는 서버 처리 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  ResponseEntity<ClothesDto> update(
      UUID clothesId,
      @Parameter(
          description = "옷 수정 요청",
          required = true,
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ClothesUpdateRequest.class)
          )
      )
      ClothesUpdateRequest request,
      MultipartFile image,
      @Parameter(hidden = true) Authentication authentication
  );
}
