package com.project.sb13ootbsteam04.feed.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "feed_likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_feed_likes_feed_user",
                        columnNames = {"feed_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedLike extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_id", nullable = false)
    private Feed feed;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public FeedLike(Feed feed, UUID userId) {
        this.feed = feed;
        this.userId = userId;
    }
}