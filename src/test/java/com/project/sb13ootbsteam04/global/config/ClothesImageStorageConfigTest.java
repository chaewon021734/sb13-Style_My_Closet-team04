package com.project.sb13ootbsteam04.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.UnavailableClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.s3.S3ClothesImageProperties;
import com.project.sb13ootbsteam04.clothes.storage.s3.S3ClothesImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("의상 이미지 저장소 설정")
class ClothesImageStorageConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(ClothesImageStorageConfig.class);

  @Test
  @DisplayName("버킷이 없으면 이미지 미첨부 등록을 지원하는 비활성 저장소를 사용한다")
  void 저장소_미설정() {
    // given & when & then
    contextRunner.run(context -> assertThat(context)
        .hasSingleBean(ClothesImageStorage.class)
        .getBean(ClothesImageStorage.class)
        .isInstanceOf(UnavailableClothesImageStorage.class));
  }

  @Test
  @DisplayName("실제 AWS 설정이면 S3 이미지 저장소를 구성한다")
  void 실제_AWS_저장소_구성() {
    // given & when & then
    contextRunner
        .withPropertyValues(
            "otboo.storage.s3.bucket=otboo-images",
            "otboo.storage.s3.region=ap-northeast-2",
            "otboo.storage.s3.public-base-url=https://cdn.example.com",
            "otboo.storage.s3.api-call-timeout=10s",
            "otboo.storage.s3.api-call-attempt-timeout=5s"
        )
        .run(context -> {
          S3ClothesImageProperties properties = context.getBean(S3ClothesImageProperties.class);

          assertThat(properties.bucket()).isEqualTo("otboo-images");
          assertThat(properties.region()).isEqualTo("ap-northeast-2");
          assertThat(properties.publicBaseUrl()).isEqualTo("https://cdn.example.com");
          assertThat(context.getBean(ClothesImageStorage.class)).isInstanceOf(S3ClothesImageStorage.class);
        });
  }

  @Test
  @DisplayName("로컬 endpoint 설정이면 path-style S3 이미지 저장소를 구성한다")
  void 로컬_S3Mock_저장소_구성() {
    // given & when & then
    contextRunner
        .withPropertyValues(
            "otboo.storage.s3.bucket=otboo-images",
            "otboo.storage.s3.region=us-east-1",
            "otboo.storage.s3.endpoint=http://localhost:9090"
        )
        .run(context -> {
          S3ClothesImageProperties properties = context.getBean(S3ClothesImageProperties.class);

          assertThat(properties.endpoint()).isEqualTo("http://localhost:9090");
          assertThat(context.getBean(ClothesImageStorage.class)).isInstanceOf(S3ClothesImageStorage.class);
        });
  }
}
