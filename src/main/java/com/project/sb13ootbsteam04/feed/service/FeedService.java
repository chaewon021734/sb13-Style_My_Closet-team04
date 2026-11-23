
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
            throw new IllegalArgumentException(
                    "피드 등록 요청은 필수입니다."
            );
        }

        if (request.authorId() == null) {
            throw new IllegalArgumentException(
                    "작성자 ID는 필수입니다."
            );
        }

        if (!currentUserId.equals(request.authorId())) {
            throw new AccessDeniedException(
                    "본인의 계정으로만 피드를 등록할 수 있습니다."
            );
        }

        if (request.weatherId() == null) {
            throw new IllegalArgumentException(
                    "날씨 ID는 필수입니다."
            );
        }

        if (!weatherRepository.existsById(request.weatherId())) {
            throw new IllegalArgumentException(
                    "존재하지 않는 날씨 ID입니다: " + request.weatherId()
            );
        }

        if (request.clothesIds() == null) {
            throw new IllegalArgumentException(
                    "의상 목록은 필수입니다."
            );
        }

        if (request.clothesIds().stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException(
                    "의상 ID에는 null이 포함될 수 없습니다."
            );
        }

        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException(
                    "피드 내용은 필수입니다."
            );
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

        Feed feed = new Feed(
                currentUserId,
                request.weatherId(),
                request.content()
        );

        Feed savedFeed = feedRepository.save(feed);

        List<FeedClothes> feedClothes = distinctClothesIds
                .stream()
                .map(clothesId ->
                        new FeedClothes(savedFeed, clothesId)
                )
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
                        new IllegalArgumentException(
                                "존재하지 않는 피드입니다."
                        )
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
    public void deleteFeed(
            UUID feedId,
            UUID currentUserId
    ) {

        validateCurrentUser(currentUserId);

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 피드입니다."
                        )
                );

        validateFeedOwner(feed, currentUserId);

        feedClothesRepository.deleteByFeed_Id(feedId);
        feedLikeRepository.deleteAllByFeedId(feedId);
        commentRepository.deleteAllByFeedId(feedId);

        feedRepository.delete(feed);
    }

    // ========================================
    // 피드 목록 조회
    // ========================================

    // 기존 호출부 호환용
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
                cursor,
                idAfter,
                limit,
                sortBy,
                sortDirection,
                keywordLike,
                authorIdEqual,
                null
        );
    }

    // 로그인 사용자 ID를 전달받는 피드 목록 조회
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

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "limit은 1 이상 100 이하여야 합니다."
            );
        }

        if (!"createdAt".equals(sortBy)) {
            throw new IllegalArgumentException(
                    "현재 지원하는 정렬 기준은 createdAt입니다."
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

        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Feed> feeds;

        if (!hasCursor) {

            if (ascending) {
                feeds = feedRepository.findFirstFeedPageAscending(
                        authorIdEqual,
                        normalizedKeyword,
                        pageable
                );
            } else {
                feeds = feedRepository.findFirstFeedPage(
                        authorIdEqual,
                        normalizedKeyword,
                        pageable
                );
            }

        } else {

            Instant cursorTime;

            try {
                cursorTime = Instant.parse(cursor);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "cursor는 ISO-8601 날짜 형식이어야 합니다.",
                        e
                );
            }

            if (ascending) {
                feeds = feedRepository.findNextFeedPageAscending(
                        authorIdEqual,
                        normalizedKeyword,
                        cursorTime,
                        idAfter,
                        pageable
                );
            } else {
                feeds = feedRepository.findNextFeedPage(
                        authorIdEqual,
                        normalizedKeyword,
                        cursorTime,
                        idAfter,
                        pageable
                );
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

            nextCursor = lastFeed.getCreatedAt().toString();
            nextIdAfter = lastFeed.getId();
        }

        long totalCount = feedRepository.countFeeds(
                authorIdEqual,
                normalizedKeyword
        );

        // 로그인 사용자의 실제 좋아요 여부 반영
        List<FeedResponse> data = pageFeeds.stream()
                .map(feed ->
                        feedResponseService.toResponse(
                                feed,
                                currentUserId
                        )
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
    // 피드 단건 조회
    // ========================================

    // 기존 호출부 호환용
    public FeedResponse getFeedResponse(UUID feedId) {
        return getFeedResponse(feedId, null);
    }

    // 로그인 사용자 ID를 전달받는 피드 단건 조회
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

        return feedResponseService.toResponse(
                feed,
                currentUserId
        );
    }

    // ========================================
    // 로그인 사용자 검증
    // ========================================

    private void validateCurrentUser(UUID currentUserId) {

        if (currentUserId == null) {
            throw new AccessDeniedException(
                    "로그인이 필요합니다."
            );
        }
    }

    // ========================================
    // 피드 작성자 권한 검증
    // ========================================

    private void validateFeedOwner(
            Feed feed,
            UUID currentUserId
    ) {

        if (!feed.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException(
                    "본인이 작성한 피드만 수정하거나 삭제할 수 있습니다."
            );
        }
    }
}
