package com.project.sb13ootbsteam04.recommendation.controller;

import com.project.sb13ootbsteam04.global.exception.ApiErrorResponse;
import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import com.project.sb13ootbsteam04.recommendation.dto.RecommendationDto;
import com.project.sb13ootbsteam04.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

  private final RecommendationService recommendationService;
  private final JwtUserIdResolver userIdResolver;

  @Operation(summary = "추천 조회", description = "추천 조회 API", responses = {
      @ApiResponse(responseCode = "200", description = "추천 조회 성공",
          content = @Content(schema = @Schema(implementation = RecommendationDto.class))),
      @ApiResponse(responseCode = "400", description = "추천 조회 실패",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<RecommendationDto> getRecommendation(
      @RequestParam UUID weatherId,
      @Parameter(hidden = true) Authentication authentication
  ) {
    UUID userId = userIdResolver.resolve(authentication);
    return ResponseEntity.ok(recommendationService.getRecommendation(weatherId, userId));
  }
}
