package com.project.sb13ootbsteam04.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Otboo 공통 예외")
class OtbooExceptionTest {

  @Test
  @DisplayName("오류 코드, 상세 정보와 원인 예외를 보존한다")
  void 공통_예외_정보_보존() {
    Throwable cause = new IllegalStateException("S3 failure");
    Map<String, String> details = new HashMap<>();
    details.put("image", "업로드 실패");

    OtbooException exception = new TestOtbooException(
        ErrorCode.CLOTHES_IMAGE_UPLOAD_FAILED,
        details,
        cause
    );
    details.put("image", "변경됨");

    assertAll(
        () -> assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.CLOTHES_IMAGE_UPLOAD_FAILED),
        () -> assertThat(exception.getMessage())
            .isEqualTo(ErrorCode.CLOTHES_IMAGE_UPLOAD_FAILED.getMessage()),
        () -> assertThat(exception.getDetails()).containsEntry("image", "업로드 실패"),
        () -> assertThat(exception.getCause()).isSameAs(cause),
        () -> assertThatThrownBy(() -> exception.getDetails().put("other", "value"))
            .isInstanceOf(UnsupportedOperationException.class)
    );
  }

  @Test
  @DisplayName("상세 정보가 없으면 빈 Map을 사용한다")
  void 상세_정보_기본값() {
    OtbooException exception = new TestOtbooException(ErrorCode.INVALID_REQUEST, null, null);

    assertThat(exception.getDetails()).isEmpty();
  }

  private static final class TestOtbooException extends OtbooException {

    private TestOtbooException(ErrorCode errorCode, Map<String, String> details, Throwable cause) {
      super(errorCode, details, cause);
    }
  }
}
