package com.project.sb13ootbsteam04.clothes.service;

import java.util.Arrays;

public enum ClothesAttributeDefSortBy {
  CREATED_AT("createdAt"),
  NAME("name");

  private final String property;

  ClothesAttributeDefSortBy(String property) {
    this.property = property;
  }

  public static ClothesAttributeDefSortBy from(String value) {
    if (value == null) {
      throw new IllegalArgumentException("정렬 기준은 필수입니다");
    }

    return Arrays.stream(values())
        .filter(sortBy -> sortBy.property.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 정렬 기준입니다: " + value));
  }

  public String property() {
    return property;
  }
}
