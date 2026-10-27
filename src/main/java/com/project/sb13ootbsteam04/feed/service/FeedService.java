
package com.project.sb13ootbsteam04.feed.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    // 피드 등록
    @Transactional
    public UUID createFeed(CreateFeedRequest request) {

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

        if (request.weatherId() == null) {
            throw new IllegalArgumentException(
                    "날씨 ID는 필수입니다."
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

        if (!userRepository.existsById(request.authorId())) {
            throw new IllegalArgumentException(
                    "존재하지 않는 사용자입니다: " + request.authorId()
            );
        }

        Feed feed = new Feed(
                request.authorId(),
                request.weatherId(),
                request.content()
        );

        Feed savedFeed = feedRepository.save(feed);

        List<FeedClothes> feedClothes = request.clothesIds()
                .stream()
                .distinct()
                .map(clothesId -> new FeedClothes(savedFeed, clothesId))
                .toList();

        feedClothesRepository.saveAll(feedClothes);

        return savedFeed.getId();
    }

    // 피드 수정
    @Transactional
    public void updateFeed(
            UUID feedId,
            UpdateFeedRequest request
    ) {

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 피드입니다."
                        )
                );

        if (request == null
                || request.content() == null
                || request.content().isBlank()) {
            throw new IllegalArgumentException(
                    "수정할 피드 내용은 필수입니다."
            );
        }

        feed.updateContent(request.content());
    }

    // 피드 삭제
    @Transactional
    public void deleteFeed(UUID feedId) {

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 피드입니다."
                        )
                );

        feedClothesRepository.deleteByFeed_Id(feedId);
        feedLikeRepository.deleteAllByFeedId(feedId);
        commentRepository.deleteAllByFeedId(feedId);

        feedRepository.delete(feed);
    }

    // 피드 목록 조회 - 오름차순 / 내림차순 지원
    public FeedPageResponse getFeedPage(
            String cursor,
            UUID idAfter,
            int limit,
            String sortBy,
            String sortDirection,
            String keywordLike,
            UUID authorIdEqual
    ) {

        // 페이지 크기 검증
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "limit은 1 이상 100 이하여야 합니다."
            );
        }

        // 현재 지원하는 정렬 기준
        if (!"createdAt".equals(sortBy)) {
            throw new IllegalArgumentException(
                    "현재 지원하는 정렬 기준은 createdAt입니다."
            );
        }

        // 정렬 방향 검증
        boolean ascending = "ASCENDING".equals(sortDirection);
        boolean descending = "DESCENDING".equals(sortDirection);

        if (!ascending && !descending) {
            throw new IllegalArgumentException(
                    "sortDirection은 ASCENDING 또는 DESCENDING이어야 합니다."
            );
        }

        // 커서 검증
        boolean hasCursor = cursor != null && !cursor.isBlank();

        if (hasCursor != (idAfter != null)) {
            throw new IllegalArgumentException(
                    "cursor와 idAfter는 함께 전달해야 합니다."
            );
        }

        // 키워드 검색 조건 정리
        String normalizedKeyword =
                keywordLike == null || keywordLike.isBlank()
                        ? null
                        : keywordLike.trim();

        // 다음 페이지 존재 여부 확인을 위해 limit + 1개 조회
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<Feed> feeds;

        if (!hasCursor) {

            // 첫 페이지 조회
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

            // 커서 문자열을 Instant로 변환
            Instant cursorTime;

            try {
                cursorTime = Instant.parse(cursor);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "cursor는 ISO-8601 날짜 형식이어야 합니다.",
                        e
                );
            }

            // 정렬 방향에 따라 다음 페이지 조회
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

        // 다음 페이지 존재 여부
        boolean hasNext = feeds.size() > limit;

        List<Feed> pageFeeds = feeds.stream()
                .limit(limit)
                .toList();

        // 다음 페이지 커서 생성
        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !pageFeeds.isEmpty()) {

            Feed lastFeed = pageFeeds.get(pageFeeds.size() - 1);

            nextCursor = lastFeed.getCreatedAt().toString();
            nextIdAfter = lastFeed.getId();
        }

        // 전체 피드 수 조회
        long totalCount = feedRepository.countFeeds(
                authorIdEqual,
                normalizedKeyword
        );

        // 응답 DTO 변환
        List<FeedResponse> data = pageFeeds.stream()
                .map(feedResponseService::toResponse)
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

    // 피드 ID로 응답 조회
    public FeedResponse getFeedResponse(UUID feedId) {

        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 피드입니다: " + feedId
                        )
                );

        return feedResponseService.toResponse(feed);
    }
}
