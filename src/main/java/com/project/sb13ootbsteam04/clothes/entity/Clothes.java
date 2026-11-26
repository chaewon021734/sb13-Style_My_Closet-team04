package com.project.sb13ootbsteam04.clothes.entity;

import com.project.sb13ootbsteam04.global.entity.UpdatedAtEntity;
import com.project.sb13ootbsteam04.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 소유한 의상과 해당 의상에 선택한 속성을 관리한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "clothes")
public class Clothes extends UpdatedAtEntity {

  private static final int IMAGE_COLUMN_LENGTH = 500;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false, updatable = false)
  private User owner;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  private ClothesType type;

  @Column(name = "image_url", length = IMAGE_COLUMN_LENGTH)
  private String imageUrl;

  // 이미지 저장소 객체 식별을 위한 내부 관리 필드. 외부에 노출하지 않는다.
  @Column(name = "image_object_key", length = IMAGE_COLUMN_LENGTH)
  private String imageObjectKey;

  @OneToMany(
      mappedBy = "clothes",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true
  )
  private List<ClothesAttribute> attributes = new ArrayList<>();

  private Clothes(User owner, String name, ClothesType type) {
    this.owner = Objects.requireNonNull(owner, "의상 소유자는 필수입니다");
    this.name = normalizeName(name);
    this.type = Objects.requireNonNull(type, "의상 종류는 필수입니다");
  }

  public static Clothes create(User owner, String name, ClothesType type) {
    return new Clothes(owner, name, type);
  }

  public List<ClothesAttribute> getAttributes() {
    return List.copyOf(attributes);
  }

  // URL은 표시용이고 키는 저장소 식별자이므로 키의 공백까지 원문으로 보존한다.
  public void updateImage(String imageUrl, String objectKey) {
    String url = imageUrl == null ? null : imageUrl.strip();
    validateImageField(url, "이미지 URL");
    validateImageField(objectKey, "이미지 객체 키");
    this.imageUrl = url;
    this.imageObjectKey = objectKey;
  }

  void registerAttribute(ClothesAttribute attribute) {
    Objects.requireNonNull(attribute, "의상 속성은 필수입니다");
    if (attribute.getClothes() != this) {
      throw new IllegalArgumentException("속성의 소속 의상이 일치하지 않습니다");
    }
    attributes.add(attribute);
  }

  private static String normalizeName(String name) {
    String normalized = Objects.requireNonNull(name, "의상 이름은 필수입니다").trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("의상 이름은 비어 있을 수 없습니다");
    }
    return normalized;
  }

  private static void validateImageField(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + "는 필수입니다");
    }
    // PostgreSQL varchar(500)의 문자 수 기준을 따른다.
    if (value.codePointCount(0, value.length()) > IMAGE_COLUMN_LENGTH) {
      throw new IllegalArgumentException(field + "는 " + IMAGE_COLUMN_LENGTH + "자 이하여야 합니다");
    }
  }
}
