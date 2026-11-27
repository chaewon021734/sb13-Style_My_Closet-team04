package com.project.sb13ootbsteam04.weather.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.global.exception.OtbooException;

import java.util.Map;

public final class WeatherForecastException extends OtbooException {

    public WeatherForecastException(ErrorCode errorCode, String reason) {
        this(errorCode, reason, null);
    }

    public WeatherForecastException(ErrorCode errorCode, String reason, Throwable cause) {
        super(errorCode, Map.of("reason", reason), cause);
    }
}
