package com.project.sb13ootbsteam04.recommendation.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.global.exception.OtbooException;
import java.util.Map;
import java.util.UUID;

public final class RecommendationException extends OtbooException {

  private RecommendationException(ErrorCode errorCode, Map<String, String> details) {
    super(errorCode, details);
  }

  public static RecommendationException weatherNotFound(UUID weatherId) {
    return new RecommendationException(
        ErrorCode.RECOMMENDATION_WEATHER_NOT_FOUND,
        Map.of("weatherId", weatherId.toString())
    );
  }
}
