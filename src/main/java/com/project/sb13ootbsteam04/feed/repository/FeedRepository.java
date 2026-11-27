
package com.project.sb13ootbsteam04.feed.repository;

import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.SkyStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

    // ========================================
    // 기존 createdAt 내림차순 조회
    // ========================================

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Feed> findFirstFeedPage(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
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
    // 기존 createdAt 오름차순 조회
    // ========================================

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
            ORDER BY f.createdAt ASC, f.id ASC
            """)
    List<Feed> findFirstFeedPageAscending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
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
    // 기존 전체 피드 수 조회
    // ========================================

    @Query("""
            SELECT COUNT(f)
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
            """)
    long countFeeds(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword
    );

    // ========================================
    // 날씨 필터 + createdAt 내림차순
    // PostgreSQL NULL 커서 자료형 오류 수정
    // ========================================

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
              AND (
                  (:skyStatus IS NULL AND :precipitationType IS NULL)
                  OR EXISTS (
                      SELECT w.id
                      FROM Weather w
                      WHERE w.id = f.weatherId
                        AND (:skyStatus IS NULL OR w.skyStatus = :skyStatus)
                        AND (
                            :precipitationType IS NULL
                            OR w.precipitationType = :precipitationType
                        )
                  )
              )
              AND (
                  CAST(:cursor AS timestamp) IS NULL
                  OR f.createdAt < :cursor
                  OR (
                      f.createdAt = :cursor
                      AND f.id < :idAfter
                  )
              )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Feed> findFilteredPageDescending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("skyStatus") SkyStatus skyStatus,
            @Param("precipitationType") PrecipitationType precipitationType,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    // ========================================
    // 날씨 필터 + createdAt 오름차순
    // PostgreSQL NULL 커서 자료형 오류 수정
    // ========================================

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
              AND (
                  (:skyStatus IS NULL AND :precipitationType IS NULL)
                  OR EXISTS (
                      SELECT w.id
                      FROM Weather w
                      WHERE w.id = f.weatherId
                        AND (:skyStatus IS NULL OR w.skyStatus = :skyStatus)
                        AND (
                            :precipitationType IS NULL
                            OR w.precipitationType = :precipitationType
                        )
                  )
              )
              AND (
                  CAST(:cursor AS timestamp) IS NULL
                  OR f.createdAt > :cursor
                  OR (
                      f.createdAt = :cursor
                      AND f.id > :idAfter
                  )
              )
            ORDER BY f.createdAt ASC, f.id ASC
            """)
    List<Feed> findFilteredPageAscending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("skyStatus") SkyStatus skyStatus,
            @Param("precipitationType") PrecipitationType precipitationType,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    // ========================================
    // 좋아요 수 내림차순 - 첫 페이지
    // ========================================

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
              AND (
                  (:skyStatus IS NULL AND :precipitationType IS NULL)
                  OR EXISTS (
                      SELECT w.id
                      FROM Weather w
                      WHERE w.id = f.weatherId
                        AND (:skyStatus IS NULL OR w.skyStatus = :skyStatus)
                        AND (
                            :precipitationType IS NULL
                            OR w.precipitationType = :precipitationType
                        )
                  )
              )
            ORDER BY
              (SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed = f) DESC,
              f.createdAt DESC,
              f.id DESC
            """)
    List<Feed> findByLikeCountDescending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("skyStatus") SkyStatus skyStatus,
            @Param("precipitationType") PrecipitationType precipitationType,
            Pageable pageable
    );

    // ========================================
    // 좋아요 수 오름차순 - 첫 페이지
    // ========================================

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
              AND (
                  (:skyStatus IS NULL AND :precipitationType IS NULL)
                  OR EXISTS (
                      SELECT w.id
                      FROM Weather w
                      WHERE w.id = f.weatherId
                        AND (:skyStatus IS NULL OR w.skyStatus = :skyStatus)
                        AND (
                            :precipitationType IS NULL
                            OR w.precipitationType = :precipitationType
                        )
                  )
              )
            ORDER BY
              (SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed = f) ASC,
              f.createdAt ASC,
              f.id ASC
            """)
    List<Feed> findByLikeCountAscending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("skyStatus") SkyStatus skyStatus,
            @Param("precipitationType") PrecipitationType precipitationType,
            Pageable pageable
    );

    // ========================================
    // 좋아요 수 내림차순 - 다음 페이지
    // ========================================

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
              AND (
                  (:skyStatus IS NULL AND :precipitationType IS NULL)
                  OR EXISTS (
                      SELECT w.id
                      FROM Weather w
                      WHERE w.id = f.weatherId
                        AND (:skyStatus IS NULL OR w.skyStatus = :skyStatus)
                        AND (
                            :precipitationType IS NULL
                            OR w.precipitationType = :precipitationType
                        )
                  )
              )
              AND (
                  (SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed = f)
                      < :cursorLikeCount
                  OR (
                      (SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed = f)
                          = :cursorLikeCount
                      AND (
                          f.createdAt < :cursorCreatedAt
                          OR (
                              f.createdAt = :cursorCreatedAt
                              AND f.id < :idAfter
                          )
                      )
                  )
              )
            ORDER BY
              (SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed = f) DESC,
              f.createdAt DESC,
              f.id DESC
            """)
    List<Feed> findNextPageByLikeCountDescending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("skyStatus") SkyStatus skyStatus,
            @Param("precipitationType") PrecipitationType precipitationType,
            @Param("cursorLikeCount") long cursorLikeCount,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    // ========================================
    // 좋아요 수 오름차순 - 다음 페이지
    // ========================================

    @Query("""
            SELECT f
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
              AND (
                  (:skyStatus IS NULL AND :precipitationType IS NULL)
                  OR EXISTS (
                      SELECT w.id
                      FROM Weather w
                      WHERE w.id = f.weatherId
                        AND (:skyStatus IS NULL OR w.skyStatus = :skyStatus)
                        AND (
                            :precipitationType IS NULL
                            OR w.precipitationType = :precipitationType
                        )
                  )
              )
              AND (
                  (SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed = f)
                      > :cursorLikeCount
                  OR (
                      (SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed = f)
                          = :cursorLikeCount
                      AND (
                          f.createdAt > :cursorCreatedAt
                          OR (
                              f.createdAt = :cursorCreatedAt
                              AND f.id > :idAfter
                          )
                      )
                  )
              )
            ORDER BY
              (SELECT COUNT(fl) FROM FeedLike fl WHERE fl.feed = f) ASC,
              f.createdAt ASC,
              f.id ASC
            """)
    List<Feed> findNextPageByLikeCountAscending(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("skyStatus") SkyStatus skyStatus,
            @Param("precipitationType") PrecipitationType precipitationType,
            @Param("cursorLikeCount") long cursorLikeCount,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    // ========================================
    // 날씨 필터 적용 전체 피드 수
    // ========================================

    @Query("""
            SELECT COUNT(f)
            FROM Feed f
            WHERE (:authorId IS NULL OR f.userId = :authorId)
              AND (
                  :keyword IS NULL
                  OR LOWER(f.content) LIKE CONCAT(
                      '%', LOWER(CAST(:keyword AS string)), '%'
                  )
              )
              AND (
                  (:skyStatus IS NULL AND :precipitationType IS NULL)
                  OR EXISTS (
                      SELECT w.id
                      FROM Weather w
                      WHERE w.id = f.weatherId
                        AND (:skyStatus IS NULL OR w.skyStatus = :skyStatus)
                        AND (
                            :precipitationType IS NULL
                            OR w.precipitationType = :precipitationType
                        )
                  )
              )
            """)
    long countFilteredFeeds(
            @Param("authorId") UUID authorId,
            @Param("keyword") String keyword,
            @Param("skyStatus") SkyStatus skyStatus,
            @Param("precipitationType") PrecipitationType precipitationType
    );
}
