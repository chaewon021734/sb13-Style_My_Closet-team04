package com.project.sb13ootbsteam04.global.exception;

import java.util.Map;
import java.util.Objects;
import lombok.Getter;

@Getter
public abstract class OtbooException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, String> details;

  protected OtbooException(ErrorCode errorCode, Map<String, String> details) {
    this(errorCode, details, null);
  }

  protected OtbooException(ErrorCode errorCode, Map<String, String> details, Throwable cause) {
    super(Objects.requireNonNull(errorCode, "오류 코드는 필수입니다").getMessage(), cause);
    this.errorCode = errorCode;
    this.details = details == null || details.isEmpty() ? Map.of() : Map.copyOf(details);
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Map<String, String> getDetails() {
    return details;
  }
}
