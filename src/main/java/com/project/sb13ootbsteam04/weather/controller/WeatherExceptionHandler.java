package com.project.sb13ootbsteam04.weather.controller;

import com.project.sb13ootbsteam04.weather.exception.WeatherLocationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Map;

// 공통 예외 응답 체계가 확정되면 공통 핸들러로 통합한다.
@RestControllerAdvice(assignableTypes = WeatherController.class)
public class WeatherExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                exception.getClass().getSimpleName(), exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(WeatherLocationException.class)
    public ResponseEntity<ErrorResponse> handleLocationFailure(WeatherLocationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorResponse(
                exception.getClass().getSimpleName(), exception.getMessage(),
                Map.of("errorCode", exception.getErrorCode().name())));
    }

    public record ErrorResponse(String exceptionName, String message, Map<String, String> details) {
    }
}
