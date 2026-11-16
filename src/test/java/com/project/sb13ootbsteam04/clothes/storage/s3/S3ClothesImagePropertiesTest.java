package com.project.sb13ootbsteam04.clothes.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("S3 의상 이미지 설정 값")
class S3ClothesImagePropertiesTest {

  @Test
  @DisplayName("제한 시간이 없으면 안전한 기본값을 적용한다")
  void 제한_시간_기본값() {
    // given & when
    S3ClothesImageProperties properties = properties(null, null);

    // then
    assertAll(
        () -> assertThat(properties.apiCallTimeout()).isEqualTo(Duration.ofSeconds(10)),
        () -> assertThat(properties.apiCallAttemptTimeout()).isEqualTo(Duration.ofSeconds(5)),
        () -> assertThat(properties.isConfigured()).isTrue()
    );
  }

  @Test
  @DisplayName("시도별 제한 시간이 전체 제한 시간보다 길면 설정을 거부한다")
  void 잘못된_제한_시간_순서() {
    // given & when & then
    assertThatThrownBy(() -> properties(Duration.ofSeconds(5), Duration.ofSeconds(6)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("전체 API 제한 시간");
  }

  @Test
  @DisplayName("제한 시간은 0보다 커야 한다")
  void 양수가_아닌_제한_시간() {
    // given & when & then
    assertAll(
        () -> assertThatThrownBy(() -> properties(Duration.ZERO, Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class),
        () -> assertThatThrownBy(() -> properties(Duration.ofSeconds(1), Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class)
    );
  }

  @Test
  @DisplayName("문자열 설정의 앞뒤 공백과 빈 값을 정규화한다")
  void 문자열_설정_정규화() {
    // given & when
    S3ClothesImageProperties normalized = new S3ClothesImageProperties(
        " otboo-images ",
        " ap-northeast-2 ",
        " ",
        " https://cdn.example.com ",
        null,
        null
    );

    // then
    assertAll(
        () -> assertThat(normalized.bucket()).isEqualTo("otboo-images"),
        () -> assertThat(normalized.region()).isEqualTo("ap-northeast-2"),
        () -> assertThat(normalized.endpoint()).isNull(),
        () -> assertThat(normalized.publicBaseUrl()).isEqualTo("https://cdn.example.com")
    );
  }

  @Test
  @DisplayName("객체 URL을 깨뜨릴 수 있는 base URL 설정을 거부한다")
  void 잘못된_URL_설정() {
    // given & when & then
    assertAll(
        () -> assertInvalidPublicBaseUrl("cdn.example.com"),
        () -> assertInvalidPublicBaseUrl("https://user:password@cdn.example.com"),
        () -> assertInvalidPublicBaseUrl("https://cdn.example.com?version=1"),
        () -> assertInvalidPublicBaseUrl("https://cdn.example.com#images")
    );
  }

  @Test
  @DisplayName("로컬 endpoint를 사용하면 브라우저용 공개 URL이 필요하다")
  void endpoint_공개_URL_필수() {
    // given & when & then
    assertThatThrownBy(() -> new S3ClothesImageProperties(
        "otboo-images",
        "us-east-1",
        "http://s3mock:9090",
        null,
        null,
        null
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미지 공개 URL");
  }

  private void assertInvalidPublicBaseUrl(String publicBaseUrl) {
    assertThatThrownBy(() -> new S3ClothesImageProperties(
        "otboo-images", "ap-northeast-2", null, publicBaseUrl, null, null
    )).isInstanceOf(IllegalArgumentException.class);
  }

  private S3ClothesImageProperties properties(Duration apiCallTimeout, Duration apiCallAttemptTimeout) {
    return new S3ClothesImageProperties(
        "otboo-images",
        "ap-northeast-2",
        null,
        "https://cdn.example.com",
        apiCallTimeout,
        apiCallAttemptTimeout
    );
  }
}
