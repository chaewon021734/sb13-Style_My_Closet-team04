
package com.project.sb13ootbsteam04.feed.repository;

import com.project.sb13ootbsteam04.feed.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    // 기존 댓글 목록 조회
    Page<Comment> findByFeed_Id(UUID feedId, Pageable pageable);

    // 피드의 전체 댓글 수
    long countByFeed_Id(UUID feedId);

    // 피드 삭제 시 댓글 일괄 삭제
    @Modifying
    @Query("DELETE FROM Comment c WHERE c.feed.id = :feedId")
    int deleteAllByFeedId(@Param("feedId") UUID feedId);

    // 첫 페이지 조회: 작성 시간 내림차순, UUID 내림차순
    @Query("""
            SELECT c
            FROM Comment c
            WHERE c.feed.id = :feedId
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    java.util.List<Comment> findFirstPage(
            @Param("feedId") UUID feedId,
            Pageable pageable
    );

    // 다음 페이지 조회: 커서보다 이전에 작성된 댓글
    @Query("""
            SELECT c
            FROM Comment c
            WHERE c.feed.id = :feedId
              AND (
                  c.createdAt < :cursor
                  OR (
                      c.createdAt = :cursor
                      AND c.id < :idAfter
                  )
              )
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    java.util.List<Comment> findNextPage(
            @Param("feedId") UUID feedId,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );
}
