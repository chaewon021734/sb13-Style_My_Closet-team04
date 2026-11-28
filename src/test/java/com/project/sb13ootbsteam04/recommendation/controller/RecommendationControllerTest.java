package com.project.sb13ootbsteam04.recommendation.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;
import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import com.project.sb13ootbsteam04.recommendation.dto.RecommendationDto;
import com.project.sb13ootbsteam04.recommendation.service.RecommendationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendationControllerTest {

  private final RecommendationService service = mock(RecommendationService.class);
  private final JwtUserIdResolver userIdResolver = mock(JwtUserIdResolver.class);
  private final Authentication authentication = mock(Authentication.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(service, userIdResolver))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void returnsRecommendationForWeatherAndAuthenticatedUser() throws Exception {
    UUID weatherId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(userIdResolver.resolve(authentication)).thenReturn(userId);
    when(service.getRecommendation(weatherId, userId))
        .thenReturn(new RecommendationDto(weatherId, userId, List.of()));

    mvc.perform(get("/api/recommendations")
            .param("weatherId", weatherId.toString())
            .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weatherId").value(weatherId.toString()))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.clothes").isArray());

    verify(service).getRecommendation(weatherId, userId);
  }

  @Test
  void rejectsMissingWeatherId() throws Exception {
    mvc.perform(get("/api/recommendations").principal(authentication))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service, userIdResolver);
  }
}
