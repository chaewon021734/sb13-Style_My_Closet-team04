package com.project.sb13ootbsteam04.clothes.storage.s3;

import com.project.sb13ootbsteam04.clothes.exception.ClothesImageStorageException;
import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.clothes.storage.StoredImage;
import java.util.Objects;
import java.util.UUID;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3ClothesImageStorage implements ClothesImageStorage { // S3 클라이언트를 사용하여 의상 이미지를 업로드하고 삭제하는 구현체

  private static final String KEY_PREFIX = "clothes/";

  private final S3Client s3Client;
  private final S3ClothesImageProperties properties;

  public S3ClothesImageStorage(
      S3Client s3Client,
      S3ClothesImageProperties properties
  ) {
    this.s3Client = Objects.requireNonNull(s3Client, "S3 클라이언트는 필수입니다");
    this.properties = Objects.requireNonNull(properties, "S3 설정은 필수입니다");
  }

  @Override
  public StoredImage upload(ClothesImageCommand image) {

    Objects.requireNonNull(image, "업로드할 이미지는 필수입니다");
    String objectKey = KEY_PREFIX + UUID.randomUUID() + extensionOf(image.contentType());
    byte[] content = image.content();
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(properties.bucket())
        .key(objectKey)
        .contentType(image.contentType())
        .contentLength((long) content.length)
        .build();

    try {
      s3Client.putObject(request, RequestBody.fromBytes(content));
      return new StoredImage(objectKey, publicUrl(objectKey));

    } catch (S3Exception | SdkClientException exception) {
      throw ClothesImageStorageException.uploadFailed(exception);
    }
  }

  @Override
  public void delete(String objectKey) {

    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("삭제할 이미지 객체 키는 필수입니다");
    }

    try {
      s3Client.deleteObject(DeleteObjectRequest.builder()
          .bucket(properties.bucket())
          .key(objectKey)
          .build());

    } catch (S3Exception | SdkClientException exception) {
      throw ClothesImageStorageException.deleteFailed(exception);
    }
  }

  @Override
  public void close() {
    s3Client.close();
  }

  private String publicUrl(String objectKey) {
    if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
      return stripTrailingSlash(properties.publicBaseUrl()) + "/" + objectKey;
    }
    // 점(.)이 포함된 버킷에서는 가상 호스트 이미지 URL이 TLS 호스트 이름 검증에 실패할 수 있으므로,
    // 점(.)이 포함된 버킷에서는 경로 기반 URL을 사용한다.
    if (properties.bucket().contains(".")) {
      return "https://s3." + properties.region() + ".amazonaws.com/" + properties.bucket() + "/" + objectKey;
    }
    return "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com/" + objectKey;
  }

  private static String stripTrailingSlash(String value) {
    int end = value.length();

    while (end > 0 && value.charAt(end - 1) == '/') {
      end--;
    }

    return value.substring(0, end);
  }

  private static String extensionOf(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/gif" -> ".gif";
      case "image/webp" -> ".webp";
      default -> throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다");
    };
  }
}
