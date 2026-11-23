
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.feed.dto.CreateFeedRequest;
import com.project.sb13ootbsteam04.feed.dto.FeedPageResponse;
import com.project.sb13ootbsteam04.feed.dto.FeedResponse;
import com.project.sb13ootbsteam04.feed.dto.UpdateFeedRequest;
import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.entity.FeedClothes;
import com.project.sb13ootbsteam04.feed.repository.CommentRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedClothesRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedLikeRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.SkyStatus;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    private final FeedRepository feedRepository;
    private final FeedClothesRepository feedClothesRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FeedResponseService feedResponseService;
    private final ClothesRepository clothesRepository;
    private final WeatherRepository weatherRepository;

    // ========================================
    // 피드 생성
    // ========================================

    @Transactional
    public UUID createFeed(
            CreateFeedRequest request,
            UUID currentUserId
    ) {
        validateCurrentUser(currentUserId);

        if (request == null) {
            throw new IllegalArgumentException("피드 등록 요청은 필수입니다.");
        }
        if (request.authorId() == null) {
            throw new IllegalArgumentException("작성자 ID는 필수입니다.");
        }
        if (!currentUserId.equals(request.authorId())) {
            throw new AccessDeniedException(
                    "본인의 계정으로만 피드를 등록할 수 있습니다."
            );
        }
        if (request.weatherId() == null) {
            throw new IllegalArgumentException("날씨 ID는 필수입니다.");
        }
        if (!weatherRepository.existsById(request.weatherId())) {
            throw new IllegalArgumentException(
                    "존재하지 않는 날씨 ID입니다: " + request.weatherId()
            );
        }
        if (request.clothesIds() == null) {
            throw new IllegalArgumentException("의상 목록은 필수입니다.");
        }
        if (request.clothesIds().stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException(
                    "의상 ID에는 null이 포함될 수 없습니다."
            );
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("피드 내용은 필수입니다.");
        }
        if (!userRepository.existsById(currentUserId)) {
            throw new IllegalArgumentException(
                    "존재하지 않는 사용자입니다: " + currentUserId
            );
        }

        List<UUID> distinctClothesIds = request.clothesIds()
                .stream()
                .distinct()
                .toList();

        if (clothesRepository.findAllById(distinctClothesIds).size()
                != distinctClothesIds.size()) {
            throw new IllegalArgumentException(
                    "존재하지 않는 의상 ID가 포함되어 있습니다."
            );
        }

        Feed savedFeed = feedRepository.save(
                new Feed(currentUserId, request.weatherId(), request.content())
        );

        List<FeedClothes> feedClothes = distinctClothesIds.stream()
                .map(id -> new FeedClothes(savedFeed, id))
                .toList();

        feedClothesRepository.saveAll(feedClothes);
        return savedFeed.getId();
    }

    // ========================================
    // 피드 수정
    // ========================================

    @Transactional
    public void updateFeed(
            UUID feedId,
            UpdateFeedRequest request,
            UUID currentUserId
    ) {
        validateCurrentUser(currentUserId);

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 피드입니다.")
                );

        validateFeedOwner(feed, currentUserId);

        if (request == null
                || request.content() == null
                || request.content().isBlank()) {
            throw new IllegalArgumentException(
                    "수정할 피드 내용은 필수입니다."
            );
        }

        feed.updateContent(request.content());
    }

    // ========================================
    // 피드 삭제
    // ========================================

    @Transactional
    public void deleteFeed(UUID feedId, UUID currentUserId) {
        validateCurrentUser(currentUserId);

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 피드입니다.")
                );

        validateFeedOwner(feed, currentUserId);

        feedClothesRepository.deleteByFeed_Id(feedId);
        feedLikeRepository.deleteAllByFeedId(feedId);
        commentRepository.deleteAllByFeedId(feedId);
        feedRepository.delete(feed);
    }

    // ========================================
    // 기존 피드 목록 조회 호환 메서드
    // ========================================

    public FeedPageResponse getFeedPage(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            String sortDirection,
            String keywordLike,
            UUID authorIdEqual
    ) {
        return getFeedPage(
                cursor, idAfter, limit, sortBy, sortDirection,
                keywordLike, null, null, authorIdEqual, null
        );
    }

    // 로그인 사용자 ID를 받는 기존 호출부 호환 메서드
    public FeedPageResponse getFeedPage(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            String sortDirection,
            String keywordLike,
            UUID authorIdEqual,
            UUID currentUserId
    ) {
        return getFeedPage(
                cursor, idAfter, limit, sortBy, sortDirection,
                keywordLike, null, null, authorIdEqual, currentUserId
        );
    }

    // ========================================
    // 날씨 필터 및 좋아요 수 정렬 지원
    // ========================================

    public FeedPageResponse getFeedPage(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            String sortDirection,
            String keywordLike,
            String skyStatusEqual,
            String precipitationTypeEqual,
            UUID authorIdEqual,
            UUID currentUserId
    ) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "limit은 1 이상 100 이하여야 합니다."
            );
        }

        if (!"createdAt".equals(sortBy) && !"likeCount".equals(sortBy)) {
            throw new IllegalArgumentException(
                    "sortBy는 createdAt 또는 likeCount여야 합니다."
            );
        }

        boolean ascending = "ASCENDING".equals(sortDirection);
        boolean descending = "DESCENDING".equals(sortDirection);

        if (!ascending && !descending) {
            throw new IllegalArgumentException(
                    "sortDirection은 ASCENDING 또는 DESCENDING이어야 합니다."
            );
        }

        boolean hasCursor = cursor != null && !cursor.isBlank();

        if (hasCursor != (idAfter != null)) {
            throw new IllegalArgumentException(
                    "cursor와 idAfter는 함께 전달해야 합니다."
            );
        }

        String normalizedKeyword =
                keywordLike == null || keywordLike.isBlank()
                        ? null
                        : keywordLike.trim();

        SkyStatus skyStatus = parseSkyStatus(skyStatusEqual);
        PrecipitationType precipitationType =
                parsePrecipitationType(precipitationTypeEqual);

        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Feed> feeds;

        if ("createdAt".equals(sortBy)) {
            Instant cursorTime = hasCursor ? parseCreatedAtCursor(cursor) : null;

            if (ascending) {
                feeds = feedRepository.findFilteredPageAscending(
                        authorIdEqual,
                        normalizedKeyword,
                        skyStatus,
                        precipitationType,
                        cursorTime,
                        idAfter,
                        pageable
                );
            } else {
                feeds = feedRepository.findFilteredPageDescending(
                        authorIdEqual,
                        normalizedKeyword,
                        skyStatus,
                        precipitationType,
                        cursorTime,
                        idAfter,
                        pageable
                );
            }
        } else {
            if (!hasCursor) {
                if (ascending) {
                    feeds = feedRepository.findByLikeCountAscending(
                            authorIdEqual,
                            normalizedKeyword,
                            skyStatus,
                            precipitationType,
                            pageable
                    );
                } else {
                    feeds = feedRepository.findByLikeCountDescending(
                            authorIdEqual,
                            normalizedKeyword,
                            skyStatus,
                            precipitationType,
                            pageable
                    );
                }
            } else {
                LikeCountCursor likeCursor = parseLikeCountCursor(cursor);

                if (ascending) {
                    feeds = feedRepository.findNextPageByLikeCountAscending(
                            authorIdEqual,
                            normalizedKeyword,
                            skyStatus,
                            precipitationType,
                            likeCursor.likeCount(),
                            likeCursor.createdAt(),
                            idAfter,
                            pageable
                    );
                } else {
                    feeds = feedRepository.findNextPageByLikeCountDescending(
                            authorIdEqual,
                            normalizedKeyword,
                            skyStatus,
                            precipitationType,
                            likeCursor.likeCount(),
                            likeCursor.createdAt(),
                            idAfter,
                            pageable
                    );
                }
            }
        }

        boolean hasNext = feeds.size() > limit;

        List<Feed> pageFeeds = feeds.stream()
                .limit(limit)
                .toList();

        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !pageFeeds.isEmpty()) {
            Feed lastFeed = pageFeeds.get(pageFeeds.size() - 1);
            nextIdAfter = lastFeed.getId();

            if ("createdAt".equals(sortBy)) {
                nextCursor = lastFeed.getCreatedAt().toString();
            } else {
                long lastLikeCount = feedLikeRepository.countByFeed_Id(
                        lastFeed.getId()
                );

                nextCursor = lastLikeCount
                        + "|"
                        + lastFeed.getCreatedAt();
            }
        }

        long totalCount = feedRepository.countFilteredFeeds(
                authorIdEqual,
                normalizedKeyword,
                skyStatus,
                precipitationType
        );

        List<FeedResponse> data = pageFeeds.stream()
                .map(feed ->
                        feedResponseService.toResponse(feed, currentUserId)
                )
                .toList();

        return new FeedPageResponse(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                sortBy,
                sortDirection
        );
    }

    // ========================================
    // 커서 처리
    // ========================================

    private Instant parseCreatedAtCursor(String cursor) {
        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "cursor는 ISO-8601 날짜 형식이어야 합니다.",
                    e
            );
        }
    }

    private LikeCountCursor parseLikeCountCursor(String cursor) {
        try {
            String[] parts = cursor.split("\\|", -1);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "좋아요 수 커서 형식이 올바르지 않습니다."
                );
            }

            long likeCount = Long.parseLong(parts[0]);
            Instant createdAt = Instant.parse(parts[1]);

            if (likeCount < 0) {
                throw new IllegalArgumentException(
                        "좋아요 수 커서는 0 이상이어야 합니다."
                );
            }

            return new LikeCountCursor(likeCount, createdAt);
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "좋아요 수 커서 형식이 올바르지 않습니다.",
                    e
            );
        }
    }

    private record LikeCountCursor(long likeCount, Instant createdAt) {
    }

    // ========================================
    // 날씨 필터 파싱
    // ========================================

    private SkyStatus parseSkyStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return SkyStatus.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 skyStatusEqual 값입니다: " + value,
                    e
            );
        }
    }

    private PrecipitationType parsePrecipitationType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return PrecipitationType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 precipitationTypeEqual 값입니다: " + value,
                    e
            );
        }
    }

    // ========================================
    // 피드 단건 조회
    // ========================================

    public FeedResponse getFeedResponse(UUID feedId) {
        return getFeedResponse(feedId, null);
    }

    public FeedResponse getFeedResponse(
            UUID feedId,
            UUID currentUserId
    ) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 피드입니다: " + feedId
                        )
                );

        return feedResponseService.toResponse(feed, currentUserId);
    }

    // ========================================
    // 권한 검증
    // ========================================

    private void validateCurrentUser(UUID currentUserId) {
        if (currentUserId == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
    }

    private void validateFeedOwner(Feed feed, UUID currentUserId) {
        if (!feed.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException(
                    "본인이 작성한 피드만 수정하거나 삭제할 수 있습니다."
            );
        }
    }
}
