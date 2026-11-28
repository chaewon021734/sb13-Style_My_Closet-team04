package com.project.sb13ootbsteam04.weather.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.global.exception.OtbooException;
import java.util.Map;

public final class WeatherLocationException extends OtbooException {

    public WeatherLocationException(ErrorCode errorCode) {
        super(errorCode, Map.of("errorCode", errorCode.name()));
    }
}
