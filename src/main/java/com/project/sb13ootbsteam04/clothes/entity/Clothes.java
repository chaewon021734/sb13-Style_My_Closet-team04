package com.project.sb13ootbsteam04.clothes.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "clothes")
public class Clothes extends BaseEntity {

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

  @Column(name = "image_url", length = 255)
  private String imageUrl;

  @OneToMany(
      mappedBy = "clothes",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true
  )
  private final List<ClothesAttribute> attributes = new ArrayList<>();

  @Builder
  public Clothes(UUID ownerId, String name, ClothesType type, String imageUrl) {
    this.ownerId = ownerId;
    this.name = name;
    this.type = type;
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
}
