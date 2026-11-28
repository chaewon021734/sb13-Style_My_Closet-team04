package com.project.sb13ootbsteam04.global.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Objects;

@Schema(name = "ErrorResponse")
public record ApiErrorResponse(
    @Schema(description = "예외 이름")
    String exceptionName,

    @Schema(description = "오류 메시지")
    String message,

    @Schema(description = "오류 부가 정보")
    Map<String, String> details
) {

  public ApiErrorResponse {
    Objects.requireNonNull(exceptionName, "예외 이름은 필수입니다");
    Objects.requireNonNull(message, "오류 메시지는 필수입니다");
    details = details == null ? Map.of() : Map.copyOf(details);
  }

  public static ApiErrorResponse from(OtbooException exception) {
    return new ApiErrorResponse(
        exception.getClass().getSimpleName(),
        exception.getMessage(),
        exception.getDetails()
    );
  }
}
