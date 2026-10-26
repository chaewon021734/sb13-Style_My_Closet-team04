package com.project.sb13ootbsteam04.clothes.entity;

import com.project.sb13ootbsteam04.global.entity.UpdatedAtEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 소유한 의상과 해당 의상에 선택한 속성을 관리한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "clothes")
public class Clothes extends UpdatedAtEntity {
//  @ManyToOne(fetch = FetchType.LAZY)
//  @JoinColumn(name = "owner_id", nullable = false)
//  private User owner;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  private ClothesType type;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @OneToMany(
      mappedBy = "clothes",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true
  )
  private List<ClothesAttribute> attributes = new ArrayList<>();

  @Builder(access = AccessLevel.PRIVATE)
  private Clothes(UUID ownerId, String name, ClothesType type, String imageUrl) {
    this.ownerId = Objects.requireNonNull(ownerId, "의상 소유자는 필수입니다");
    this.name = normalizeName(name);
    this.type = Objects.requireNonNull(type, "의상 종류는 필수입니다");
    this.imageUrl = imageUrl;
  }

  public static Clothes create(UUID ownerId, String name, ClothesType type, String imageUrl) {
    return Clothes.builder()
        .ownerId(ownerId)
        .name(name)
        .type(type)
        .imageUrl(imageUrl)
        .build();
  }

  public List<ClothesAttribute> getAttributes() {
    return List.copyOf(attributes);
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
}
