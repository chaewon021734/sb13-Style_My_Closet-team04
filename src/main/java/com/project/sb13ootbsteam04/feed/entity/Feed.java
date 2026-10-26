package com.project.sb13ootbsteam04.feed.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feed extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "weather_id")
    private UUID weatherId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    public Feed(UUID userId, UUID weatherId, String content) {
        this.userId = userId;
        this.weatherId = weatherId;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }
}