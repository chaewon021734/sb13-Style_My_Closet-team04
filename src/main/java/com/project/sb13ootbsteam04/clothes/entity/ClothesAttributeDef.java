package com.project.sb13ootbsteam04.clothes.entity;

import com.project.sb13ootbsteam04.global.entity.UpdatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 관리자가 정의한 의상 속성과 사용자가 선택할 수 있는 값을 관리한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "clothes_attribute_defs")
public class ClothesAttributeDef extends UpdatedAtEntity {

  @Column(nullable = false, length = 100)
  private String name;

  /** 선택 가능한 문자열을 순서대로 보존하는 PostgreSQL JSONB 배열 */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(
      name = "selectable_values",
      columnDefinition = "jsonb",
      nullable = false
  )
  private List<String> selectableValues = new ArrayList<>();

  private ClothesAttributeDef(String name, List<String> selectableValues) {
    this.name = normalizeName(name);
    this.selectableValues = normalizeValues(selectableValues);
  }

  public static ClothesAttributeDef create(String name, List<String> selectableValues) {
    return new ClothesAttributeDef(name, selectableValues);
  }

  public List<String> getSelectableValues() {
    return List.copyOf(selectableValues);
  }


  private static String normalizeValue(String value) {
    String normalized = Objects.requireNonNull(value, "선택지는 null일 수 없습니다").trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("선택지는 비어 있을 수 없습니다");
    }
    return normalized;
  }

  private static String normalizeName(String name) {
    String normalized = Objects.requireNonNull(name, "속성 이름은 필수입니다").trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("속성 이름은 비어 있을 수 없습니다");
    }
    return normalized;
  }

  private static List<String> normalizeValues(List<String> values) {
    Objects.requireNonNull(values, "선택지 목록은 필수입니다");
    if (values.isEmpty()) {
      throw new IllegalArgumentException("선택지는 최소 하나 이상 필요합니다");
    }

    List<String> normalizedValues = new ArrayList<>(values.size());
    Set<String> uniqueValues = new HashSet<>();
    for (String value : values) {
      String normalized = normalizeValue(value);
      if (!uniqueValues.add(normalized.toLowerCase(Locale.ROOT))) { // Locale.ROOT를 사용하여 모든 환경에서 일관되게 비교
        throw new IllegalArgumentException("중복된 선택지입니다");
      }
      normalizedValues.add(normalized);
    }
    return normalizedValues;
  }


  String resolveSelectableValue(String value) {
    return selectableValues.stream()
        .filter(selectableValue -> selectableValue.equalsIgnoreCase(normalizeValue(value)))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("선택할 수 없는 속성값입니다"));
  }

}
