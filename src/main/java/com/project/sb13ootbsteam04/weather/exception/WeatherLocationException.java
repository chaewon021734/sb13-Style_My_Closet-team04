package com.project.sb13ootbsteam04.weather.exception;

import com.project.sb13ootbsteam04.global.exception.ExceptionHandler;
import com.project.sb13ootbsteam04.global.exception.ErrorCode;

public class WeatherLocationException extends ExceptionHandler {

    public WeatherLocationException(String message) {
        super(message);
    }

    public ErrorCode getErrorCode() {
        return ErrorCode.WEATHER_LOCATION_LOOKUP_FAILED;
    }
}
