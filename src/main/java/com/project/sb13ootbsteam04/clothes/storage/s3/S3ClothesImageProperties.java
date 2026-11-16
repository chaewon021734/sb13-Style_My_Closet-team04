package com.project.sb13ootbsteam04.clothes.storage.s3;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.storage.s3")
public record S3ClothesImageProperties(
    String bucket,
    String region,
    String endpoint,
    String publicBaseUrl,
    Duration apiCallTimeout,
    Duration apiCallAttemptTimeout
) {

  private static final Duration DEFAULT_API_CALL_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DEFAULT_API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

  public S3ClothesImageProperties {
    bucket = normalize(bucket);
    region = normalize(region);
    endpoint = normalizeUrl(endpoint, "S3 endpoint");
    publicBaseUrl = normalizeUrl(publicBaseUrl, "이미지 공개 URL");
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
    return bucket != null && region != null;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip();
  }

  private static String normalizeUrl(String value, String propertyName) {
    String normalized = normalize(value);
    if (normalized == null) {
      return null;
    }

    URI uri;
    try {
      uri = URI.create(normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(propertyName + " 형식이 올바르지 않습니다", exception);
    }

    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
      throw new IllegalArgumentException(propertyName + "은 http 또는 https URL이어야 합니다");
    }
    // query나 fragment가 있는 base URL 뒤에 /clothes/{objectKey} 를 붙이면 잘못된 imageUrl이 만들어질 수 있으므로 이를 차단한다.
    if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalArgumentException(propertyName + "에 사용자 정보, query, fragment를 포함할 수 없습니다");
    }
    return normalized;
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
