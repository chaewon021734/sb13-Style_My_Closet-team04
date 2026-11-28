package com.project.sb13ootbsteam04.auth.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.global.exception.OtbooException;

import java.util.Map;

public class TemporaryPasswordRateLimitException extends OtbooException {

    public TemporaryPasswordRateLimitException() {
        super(
                ErrorCode.TEMPORARY_PASSWORD_RATE_LIMIT_EXCEEDED,
                Map.of()
        );
    }
}
