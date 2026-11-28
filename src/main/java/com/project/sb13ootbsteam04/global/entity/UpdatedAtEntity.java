package com.project.sb13ootbsteam04.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.LastModifiedDate;

@ToString(callSuper = true)
@Getter
@MappedSuperclass
public abstract class UpdatedAtEntity extends CreatedAtEntity {

  @LastModifiedDate
  @Column(name = "updated_at")
  private Instant updatedAt;
}
