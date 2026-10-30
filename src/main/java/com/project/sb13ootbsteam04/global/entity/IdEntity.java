package com.project.sb13ootbsteam04.global.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;

/** 생성·수정 시각이 필요하지 않은 엔티티에 UUID 식별자를 제공한다. */
@MappedSuperclass
@Getter
@ToString
public abstract class IdEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
}
