package com.project.sb13ootbsteam04.clothes.storage.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.storage.s3")
public record S3ClothesImageProperties(
    String bucket,
    String region,
    String publicBaseUrl
) {

  public boolean isConfigured() {
    return bucket != null && !bucket.isBlank() && region != null && !region.isBlank();
  }
}
