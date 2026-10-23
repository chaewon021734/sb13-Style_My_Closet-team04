package com.project.sb13ootbsteam04.clothes.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "clothes_attribute")
public class ClothesAttribute extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "clothes_id", nullable = false)
  private Clothes clothes;

//  @ManyToOne(fetch = FetchType.LAZY)
//  @JoinColumn(name = "definition_id", nullable = false)
//  private ClothsAttributeDef def;
  @Column(name = "definition_id", nullable = false)
  private UUID definitionId;

  @Column(nullable = false, length = 100)
  private String value;

  @Builder
  public ClothesAttribute(Clothes clothes, UUID definitionId, String value) {
    this.clothes = clothes;
    this.definitionId = definitionId;
    this.value = value;
  }

  public static ClothesAttribute create(Clothes clothes, UUID definitionId, String value) {
    return ClothesAttribute.builder()
        .clothes(clothes)
        .definitionId(definitionId)
        .value(value)
        .build();
  }
}
