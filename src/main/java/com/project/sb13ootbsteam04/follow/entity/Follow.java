package com.project.sb13ootbsteam04.follow.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "follows",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_follows_follower_following",
                        columnNames = {"follower_id", "following_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseEntity {

    @Column(name = "follower_id", nullable = false)
    private UUID followerId;

    @Column(name = "following_id", nullable = false)
    private UUID followingId;

    public Follow(UUID followerId, UUID followingId) {
        if (followerId == null || followingId == null) {
            throw new IllegalArgumentException("팔로워와 팔로잉 사용자 ID는 필수입니다.");
        }

        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("자기 자신을 팔로우할 수 없습니다.");
        }

        this.followerId = followerId;
        this.followingId = followingId;
    }
}