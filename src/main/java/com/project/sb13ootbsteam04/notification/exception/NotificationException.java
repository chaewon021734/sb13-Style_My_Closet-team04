package com.project.sb13ootbsteam04.notification.exception;

import com.project.sb13ootbsteam04.global.exception.ErrorCode;
import com.project.sb13ootbsteam04.global.exception.OtbooException;
import java.util.Map;
import java.util.UUID;

public final class NotificationException extends OtbooException {

  private NotificationException(ErrorCode errorCode, Map<String, String> details) {
    super(errorCode, details);
  }

  public static NotificationException notFound(UUID notificationId) {
    return new NotificationException(
        ErrorCode.NOTIFICATION_NOT_FOUND,
        Map.of("notificationId", notificationId.toString())
    );
  }

  public static NotificationException forbidden(UUID notificationId) {
    return new NotificationException(
        ErrorCode.NOTIFICATION_FORBIDDEN,
        Map.of("notificationId", notificationId.toString())
    );
  }
}
