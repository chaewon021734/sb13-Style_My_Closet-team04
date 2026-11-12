package com.project.sb13ootbsteam04.global.config;

import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.UnavailableClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.s3.S3ClothesImageProperties;
import com.project.sb13ootbsteam04.clothes.storage.s3.S3ClothesImageStorage;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(S3ClothesImageProperties.class)
public class ClothesImageStorageConfig {

  @Bean
  public ClothesImageStorage clothesImageStorage(S3ClothesImageProperties properties) {
    if (!properties.isConfigured()) {
      log.warn("의상 이미지 저장소가 비활성화되어 있습니다.");
      return new UnavailableClothesImageStorage();
    }

    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(properties.region()))
        .overrideConfiguration(configuration -> configuration
            .apiCallTimeout(properties.apiCallTimeout())
            .apiCallAttemptTimeout(properties.apiCallAttemptTimeout()));

    configureEndpointAndCredentials(builder, properties);
    S3Client s3Client = builder.build();

    String mode = StringUtils.hasText(properties.endpoint()) ? "local" : "aws";
    log.info("의상 이미지 저장소가 활성화되었습니다. mode={}, region={}", mode, properties.region());
    return new S3ClothesImageStorage(s3Client, properties);
  }

  private void configureEndpointAndCredentials(S3ClientBuilder builder, S3ClothesImageProperties properties) {
    if (StringUtils.hasText(properties.endpoint())) {
      // endpoint는 자격증명이 필요 없는 로컬 S3Mock 통합 테스트 전용이다.
      builder.endpointOverride(URI.create(properties.endpoint()))
          .serviceConfiguration(S3Configuration.builder()
              .pathStyleAccessEnabled(true)
              .build())
          .credentialsProvider(AnonymousCredentialsProvider.create());
      return;
    }

    // AWS SDK 기본 자격 증명 체인을 사용해 로컬 프로필과 ECS Task Role을 지원한다.
    builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
  }
}
