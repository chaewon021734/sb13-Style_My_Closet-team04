package com.project.sb13ootbsteam04.global.config;

import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.UnavailableClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.s3.S3ClothesImageProperties;
import com.project.sb13ootbsteam04.clothes.storage.s3.S3ClothesImageStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3ClothesImageProperties.class)
public class ClothesImageStorageConfig {

  @Bean
  public ClothesImageStorage clothesImageStorage(S3ClothesImageProperties properties) {
    if (!properties.isConfigured()) {
      return new UnavailableClothesImageStorage();
    }

    S3Client s3Client = S3Client.builder()
        .region(Region.of(properties.region()))
        .overrideConfiguration(configuration -> configuration
            .apiCallTimeout(properties.apiCallTimeout())
            .apiCallAttemptTimeout(properties.apiCallAttemptTimeout()))
        .build();

    return new S3ClothesImageStorage(s3Client, properties);
  }
}
