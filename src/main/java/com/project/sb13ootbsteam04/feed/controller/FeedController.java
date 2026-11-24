package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.dto.CreateFeedRequest;
import com.project.sb13ootbsteam04.feed.dto.FeedPageResponse;
import com.project.sb13ootbsteam04.feed.dto.FeedResponse;
import com.project.sb13ootbsteam04.feed.dto.UpdateFeedRequest;
import com.project.sb13ootbsteam04.feed.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final JwtUserIdResolver jwtUserIdResolver;

    // ========================================
    // 피드 목록 조회
    // ========================================

    @Operation(summary = "피드 목록 조회", description = "좋아요 수(likeCount) 정렬은 각 요청 시점의 값을 기준으로 합니다. 페이지 조회 사이에 좋아요 수가 변경되면 피드가 중복되거나 누락될 수 있으며, 페이지 간 정렬 일관성을 보장하지 않습니다.")
    @GetMapping
    public ResponseEntity<FeedPageResponse> getFeeds(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam @Min(1) @Max(100) int limit,
            @RequestParam String sortBy,
            @RequestParam String sortDirection,
            @RequestParam(required = false) String keywordLike,
            @RequestParam(required = false) String skyStatusEqual,
            @RequestParam(required = false) String precipitationTypeEqual,
            @RequestParam(required = false) UUID authorIdEqual,
            Authentication authentication
    ) {
        UUID currentUserId = jwtUserIdResolver.resolve(authentication);

        FeedPageResponse response = feedService.getFeedPage(
                cursor,
                idAfter,
                limit,
                sortBy,
                sortDirection,
                keywordLike,
                skyStatusEqual,
                precipitationTypeEqual,
                authorIdEqual,
                currentUserId
        );

        return ResponseEntity.ok(response);
    }

    // ========================================
    // 피드 생성
    // ========================================

    @PostMapping
    public ResponseEntity<FeedResponse> createFeed(
            @RequestBody CreateFeedRequest request,
            Authentication authentication
    ) {
        UUID currentUserId = jwtUserIdResolver.resolve(authentication);

        UUID feedId = feedService.createFeed(request, currentUserId);

        FeedResponse response = feedService.getFeedResponse(
                feedId,
                currentUserId
        );

        return ResponseEntity
                .created(URI.create("/api/feeds/" + feedId))
                .body(response);
    }

    // ========================================
    // 피드 수정
    // ========================================

    @PatchMapping("/{feedId}")
    public ResponseEntity<FeedResponse> updateFeed(
            @PathVariable UUID feedId,
            @RequestBody UpdateFeedRequest request,
            Authentication authentication
    ) {
        UUID currentUserId = jwtUserIdResolver.resolve(authentication);

        feedService.updateFeed(feedId, request, currentUserId);

        FeedResponse response = feedService.getFeedResponse(
                feedId,
                currentUserId
        );

        return ResponseEntity.ok(response);
    }

    // ========================================
    // 피드 삭제
    // ========================================

    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> deleteFeed(
            @PathVariable UUID feedId,
            Authentication authentication
    ) {
        UUID currentUserId = jwtUserIdResolver.resolve(authentication);

        feedService.deleteFeed(feedId, currentUserId);

        return ResponseEntity.noContent().build();
    }
}
