package com.project.sb13ootbsteam04.feed.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "feed_clothes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_feed_clothes_feed_clothes",
                        columnNames = {"feed_id", "clothes_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedClothes extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_id", nullable = false)
    private Feed feed;

    @Column(name = "clothes_id", nullable = false)
    private UUID clothesId;

    public FeedClothes(Feed feed, UUID clothesId) {
        if (feed == null || clothesId == null) {
            throw new IllegalArgumentException("피드와 의상 ID는 필수입니다.");
        }

        this.feed = feed;
        this.clothesId = clothesId;
    }
}