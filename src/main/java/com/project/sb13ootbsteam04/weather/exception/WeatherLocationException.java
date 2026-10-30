package com.project.sb13ootbsteam04.weather.exception;

import com.project.sb13ootbsteam04.global.exception.ExceptionHandler;
import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Objects;

public class WeatherLocationException extends ExceptionHandler {

    private final HttpStatus status;

    public WeatherLocationException(String message) {
        this(HttpStatus.BAD_GATEWAY, message);
    }

    public WeatherLocationException(HttpStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return ErrorCode.WEATHER_LOCATION_LOOKUP_FAILED;
    }
}
