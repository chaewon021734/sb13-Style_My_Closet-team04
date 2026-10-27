package com.project.sb13ootbsteam04.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

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

  @Test
  @DisplayName("IllegalArgumentException의 내부 메시지를 응답에 노출하지 않는다")
  void 잘못된_인자_내부_메시지_비노출() {
    IllegalArgumentException exception = new IllegalArgumentException(
        "jdbc:postgresql://internal-db:5432/otboo"
    );

    ResponseEntity<ApiErrorResponse> response = handler.handleIllegalArgument(exception);

    assertAll(
        () -> assertThat(response.getStatusCode().value()).isEqualTo(400),
        () -> assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
            "IllegalArgumentException",
            ErrorCode.INVALID_REQUEST.getMessage(),
            Map.of()
        )),
        () -> assertThat(response.getBody().message()).doesNotContain("internal-db")
    );
  }

  @Test
  @DisplayName("Spring 프레임워크의 4xx 예외 상태를 500으로 변경하지 않는다")
  void 프레임워크_클라이언트_오류_상태_보존() {

    HttpRequestMethodNotSupportedException exception =
        new HttpRequestMethodNotSupportedException("POST", List.of("GET"));

    ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(exception);

    assertAll(
        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED),
        () -> assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
            "HttpRequestMethodNotSupportedException",
            ErrorCode.INVALID_REQUEST.getMessage(),
            Map.of()
        ))
    );
  }

  @Test
  @DisplayName("예상하지 못한 서버 예외는 내부 정보를 숨긴 500 응답으로 변환한다")
  void 예상하지_못한_서버_오류_응답() {
    RuntimeException exception = new RuntimeException("internal-secret");

    ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(exception);

    assertAll(
        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR),
        () -> assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
            "Exception",
            ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
            Map.of()
        )),
        () -> assertThat(response.getBody().message()).doesNotContain("internal-secret")
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
