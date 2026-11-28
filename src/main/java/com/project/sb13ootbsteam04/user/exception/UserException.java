package com.project.sb13ootbsteam04.user.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.global.exception.OtbooException;
import java.util.Map;
import java.util.UUID;

public final class UserException extends OtbooException {

  private UserException(ErrorCode errorCode, Map<String, String> details) {
    super(errorCode, details);
  }

  public static UserException profileForbidden(UUID userId) {
    return new UserException(
        ErrorCode.PROFILE_FORBIDDEN,
        Map.of("userId", userId.toString())
    );
  }

  public static UserException userNotFound(UUID userId) {
    return new UserException(
        ErrorCode.USER_NOT_FOUND,
        Map.of("userId", userId.toString())
    );
  }
}
