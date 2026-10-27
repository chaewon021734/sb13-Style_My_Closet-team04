
package com.project.sb13ootbsteam04.feed.repository;

import com.project.sb13ootbsteam04.feed.entity.Feed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

    // ========================================
    // 내림차순(DESCENDING) 조회
    // ========================================

    // 첫 페이지 조회 - 최신 피드부터
    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE LOWER(
                      CONCAT('%', :keyword, '%')
                  )
              )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Feed> findFirstFeedPage(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 다음 페이지 조회 - 내림차순
    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE LOWER(
                      CONCAT('%', :keyword, '%')
                  )
              )
              AND (
                  f.createdAt < :cursor
                  OR (
                      f.createdAt = :cursor
                      AND f.id < :idAfter
                  )
              )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Feed> findNextFeedPage(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    // ========================================
    // 오름차순(ASCENDING) 조회
    // ========================================

    // 첫 페이지 조회 - 오래된 피드부터
    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE LOWER(
                      CONCAT('%', :keyword, '%')
                  )
              )
            ORDER BY f.createdAt ASC, f.id ASC
            """)
    List<Feed> findFirstFeedPageAscending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 다음 페이지 조회 - 오름차순
    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE LOWER(
                      CONCAT('%', :keyword, '%')
                  )
              )
              AND (
                  f.createdAt > :cursor
                  OR (
                      f.createdAt = :cursor
                      AND f.id > :idAfter
                  )
              )
            ORDER BY f.createdAt ASC, f.id ASC
            """)
    List<Feed> findNextFeedPageAscending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    // ========================================
    // 전체 피드 수 조회
    // ========================================

    @Query("""
            SELECT COUNT(f)
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE LOWER(
                      CONCAT('%', :keyword, '%')
                  )
              )
            """)
    long countFeeds(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword
    );
}
