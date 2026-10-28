package com.project.sb13ootbsteam04.global.exception;

import com.project.sb13ootbsteam04.user.exception.DuplicateEmailException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  // User 도메인: 이메일 중복 예외 처리
  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateEmail(
          DuplicateEmailException exception
  ) {
    log.warn("이메일 중복 요청이 발생했습니다.");

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("message", exception.getMessage()));
  }

  // 공통 비즈니스 예외 처리
  @ExceptionHandler(OtbooException.class)
  public ResponseEntity<ApiErrorResponse> handleOtbooException(OtbooException exception) {
    HttpStatus status = exception.getErrorCode().getStatus();
    logOtbooException(exception, status);

    return ResponseEntity.status(status).body(ApiErrorResponse.from(exception));
  }

  // 입력값 검증 실패
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> details = new LinkedHashMap<>();

    exception.getBindingResult().getGlobalErrors().forEach(error ->
            details.merge(
                    error.getObjectName(),
                    error.getDefaultMessage() == null ? "올바르지 않은 값입니다" : error.getDefaultMessage(),
                    (first, next) -> first + "; " + next
            ));

    exception.getBindingResult().getFieldErrors().forEach(error ->
            details.merge(
                    error.getField(),
                    error.getDefaultMessage() == null ? "올바르지 않은 값입니다" : error.getDefaultMessage(),
                    (first, next) -> first + "; " + next
            ));

    log.warn("요청 값 검증에 실패했습니다. details={}", details);

    return ResponseEntity.badRequest().body(new ApiErrorResponse(
            exception.getClass().getSimpleName(),
            ErrorCode.INVALID_REQUEST.getMessage(),
            details
    ));
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException exception) {
    log.warn("필수 multipart 항목이 없습니다. part={}", exception.getRequestPartName());

    return ResponseEntity.badRequest().body(new ApiErrorResponse(
            exception.getClass().getSimpleName(),
            "필수 multipart 항목이 없습니다",
            Map.of(exception.getRequestPartName(), "필수 항목입니다")
    ));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
    log.warn("업로드 가능한 파일 크기를 초과했습니다.", exception);

    return ResponseEntity.badRequest().body(new ApiErrorResponse(
            exception.getClass().getSimpleName(),
            "업로드 가능한 파일 크기를 초과했습니다",
            Map.of("image", "파일 크기를 확인해 주세요")
    ));
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ApiErrorResponse> handleMultipart(MultipartException exception) {
    log.warn("multipart 요청을 처리할 수 없습니다.", exception);

    return ResponseEntity.badRequest().body(new ApiErrorResponse(
            exception.getClass().getSimpleName(),
            "multipart 요청을 처리할 수 없습니다",
            Map.of("request", "multipart/form-data 형식을 확인해 주세요")
    ));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
    log.warn("요청 값이 도메인 규칙을 위반했습니다.", exception);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(
            exception.getClass().getSimpleName(),
            ErrorCode.INVALID_REQUEST.getMessage(),
            Map.of()
    ));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
    log.warn("request JSON을 읽을 수 없습니다. message={}", exception.getMessage());

    return ResponseEntity.badRequest().body(new ApiErrorResponse(
            exception.getClass().getSimpleName(),
            "request JSON을 읽을 수 없습니다",
            Map.of("request", "JSON 형식과 enum 값을 확인해 주세요")
    ));
  }

  @ExceptionHandler({
          MethodArgumentTypeMismatchException.class,
          MissingServletRequestParameterException.class,
          MissingRequestHeaderException.class,
          ConstraintViolationException.class,
          BindException.class
  })
  public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
    log.warn(
            "요청 형식이 올바르지 않습니다. exceptionType={}, message={}",
            exception.getClass().getSimpleName(),
            exception.getMessage()
    );

    return ResponseEntity.badRequest().body(new ApiErrorResponse(
            exception.getClass().getSimpleName(),
            ErrorCode.INVALID_REQUEST.getMessage(),
            Map.of("request", "요청 형식을 확인해 주세요")
    ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
    if (exception instanceof ErrorResponse errorResponse
            && errorResponse.getStatusCode().is4xxClientError()) {
      log.warn(
              "프레임워크 요청 예외가 발생했습니다. exceptionType={}",
              exception.getClass().getSimpleName()
      );

      return ResponseEntity.status(errorResponse.getStatusCode())
              .headers(errorResponse.getHeaders())
              .body(new ApiErrorResponse(
                      exception.getClass().getSimpleName(),
                      ErrorCode.INVALID_REQUEST.getMessage(),
                      Map.of()
              ));
    }

    log.error("처리되지 않은 서버 오류가 발생했습니다.", exception);

    return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
            .body(new ApiErrorResponse(
                    "Exception",
                    ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                    Map.of()
            ));
  }

  private void logOtbooException(OtbooException exception, HttpStatus status) {
    if (status.is5xxServerError()) {
      log.error(
              "서버 오류가 발생했습니다. errorCode={}, message={}",
              exception.getErrorCode().getCode(),
              exception.getMessage(),
              exception
      );
      return;
    }

    if (exception.getCause() != null) {
      log.warn(
              "비즈니스 예외가 발생했습니다. errorCode={}, message={}",
              exception.getErrorCode().getCode(),
              exception.getMessage(),
              exception
      );
      return;
    }

    log.warn(
            "비즈니스 예외가 발생했습니다. errorCode={}, message={}",
            exception.getErrorCode().getCode(),
            exception.getMessage()
    );
  }
}
