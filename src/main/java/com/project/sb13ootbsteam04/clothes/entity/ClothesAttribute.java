package com.project.sb13ootbsteam04.clothes.entity;

import com.project.sb13ootbsteam04.global.entity.IdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 의상에 선택된 속성 정의와 그 값을 저장한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "clothes_attributes",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_clothes_attributes_clothes_definition",
        columnNames = {"clothes_id", "definition_id"}
    ),
    indexes = @Index(name = "idx_clothes_attributes_definition_id", columnList = "definition_id")
)
public class ClothesAttribute extends IdEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "clothes_id", nullable = false)
  private Clothes clothes;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "definition_id", nullable = false)
  private ClothesAttributeDef definition;

  @Column(nullable = false, length = 100)
  private String value;

  private ClothesAttribute(Clothes clothes, ClothesAttributeDef definition, String value) {
    this.clothes = Objects.requireNonNull(clothes, "소속 의상은 필수입니다");
    this.definition = Objects.requireNonNull(definition, "속성 정의는 필수입니다");
    this.value = this.definition.resolveSelectableValue(value);
  }

  public static ClothesAttribute create(Clothes clothes, ClothesAttributeDef definition, String value) {
    ClothesAttribute attribute = new ClothesAttribute(clothes, definition, value);
    clothes.registerAttribute(attribute);
    return attribute;
  }
}
