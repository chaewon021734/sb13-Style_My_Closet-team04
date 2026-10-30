package com.project.sb13ootbsteam04.clothes.storage;

public record StoredImage(String objectKey, String url) {

  public StoredImage {
    objectKey = requireText(objectKey, "이미지 객체 키");
    url = requireText(url, "이미지 URL");
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + "는 필수입니다");
    }
    return value;
  }
}
