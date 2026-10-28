package com.project.sb13ootbsteam04.clothes.storage.s3;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.storage.s3")
public record S3ClothesImageProperties(
    String bucket,
    String region,
    String publicBaseUrl,
    Duration apiCallTimeout,
    Duration apiCallAttemptTimeout
) {

  private static final Duration DEFAULT_API_CALL_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DEFAULT_API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

  public S3ClothesImageProperties {
    apiCallTimeout = defaultIfNull(apiCallTimeout, DEFAULT_API_CALL_TIMEOUT);
    apiCallAttemptTimeout = defaultIfNull(
        apiCallAttemptTimeout,
        DEFAULT_API_CALL_ATTEMPT_TIMEOUT
    );

    if (apiCallAttemptTimeout.compareTo(apiCallTimeout) > 0) {
      throw new IllegalArgumentException("S3 시도별 제한 시간은 전체 API 제한 시간보다 길 수 없습니다");
    }
  }

  public boolean isConfigured() {
    return bucket != null && !bucket.isBlank() && region != null && !region.isBlank();
  }

  private static Duration defaultIfNull(Duration value, Duration defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("S3 제한 시간은 0보다 커야 합니다");
    }
    return value;
  }
}
