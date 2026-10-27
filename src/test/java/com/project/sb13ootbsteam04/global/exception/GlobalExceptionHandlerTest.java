package com.project.sb13ootbsteam04.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

@DisplayName("전역 예외 처리")
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("공통 예외를 Swagger ErrorResponse 구조로 변환한다")
  void 공통_예외_응답_변환() {
    OtbooException exception = new TestOtbooException(
        ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND,
        Map.of("definitionIds", "definition-1")
    );

    ResponseEntity<ApiErrorResponse> response = handler.handleOtbooException(exception);

    assertAll(
        () -> assertThat(response.getStatusCode())
            .isEqualTo(ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND.getStatus()),
        () -> assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
            "TestOtbooException",
            ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND.getMessage(),
            Map.of("definitionIds", "definition-1")
        ))
    );
  }

  @Test
  @DisplayName("오류 응답 스키마는 공식 OpenAPI의 세 필드만 제공한다")
  void 오류_응답_Swagger_스키마() {
    Schema<?> schema = ModelConverters.getInstance()
        .read(ApiErrorResponse.class)
        .get("ErrorResponse");

    assertAll(
        () -> assertThat(schema.getProperties())
            .containsOnlyKeys("exceptionName", "message", "details"),
        () -> assertThat(property(schema, "exceptionName").getDescription()).isEqualTo("예외 이름"),
        () -> assertThat(property(schema, "message").getDescription()).isEqualTo("오류 메시지"),
        () -> assertThat(property(schema, "details").getDescription()).isEqualTo("오류 부가 정보")
    );
  }

  private Schema<?> property(Schema<?> schema, String name) {
    return (Schema<?>) schema.getProperties().get(name);
  }

  private static final class TestOtbooException extends OtbooException {

    private TestOtbooException(ErrorCode errorCode, Map<String, String> details) {
      super(errorCode, details);
    }
  }
}
