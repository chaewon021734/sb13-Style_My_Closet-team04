
package com.project.sb13ootbsteam04.feed.repository;

import com.project.sb13ootbsteam04.feed.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    // 특정 피드의 댓글 목록 조회
    Page<Comment> findByFeed_Id(UUID feedId, Pageable pageable);

    // 특정 피드의 댓글 개수 조회
    long countByFeed_Id(UUID feedId);

    // 특정 피드의 댓글 전체 삭제
    @Modifying
    @Query("DELETE FROM Comment c WHERE c.feed.id = :feedId")
    int deleteAllByFeedId(@Param("feedId") UUID feedId);
}
