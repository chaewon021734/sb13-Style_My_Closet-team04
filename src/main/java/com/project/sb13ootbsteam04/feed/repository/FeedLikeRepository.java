
package com.project.sb13ootbsteam04.feed.repository;

import com.project.sb13ootbsteam04.feed.entity.FeedLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID> {

    // 특정 사용자의 좋아요 여부 확인
    boolean existsByFeed_IdAndUserId(UUID feedId, UUID userId);

    // 특정 사용자의 좋아요 조회
    Optional<FeedLike> findByFeed_IdAndUserId(UUID feedId, UUID userId);

    // 특정 피드의 좋아요 개수 조회
    long countByFeed_Id(UUID feedId);

    // 특정 피드의 좋아요 전체 삭제
    @Modifying
    @Query("DELETE FROM FeedLike fl WHERE fl.feed.id = :feedId")
    int deleteAllByFeedId(@Param("feedId") UUID feedId);
}
