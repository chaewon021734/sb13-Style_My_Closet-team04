
package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.dto.CreateFeedRequest;
import com.project.sb13ootbsteam04.feed.dto.FeedPageResponse;
import com.project.sb13ootbsteam04.feed.dto.FeedResponse;
import com.project.sb13ootbsteam04.feed.dto.UpdateFeedRequest;
import com.project.sb13ootbsteam04.feed.service.FeedService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
@Validated
public class FeedController {

    private final FeedService feedService;

    // 피드 목록 조회
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
            @RequestParam(required = false) UUID authorIdEqual
    ) {

        if (skyStatusEqual != null && !skyStatusEqual.isBlank()) {
            throw new IllegalArgumentException(
                    "skyStatusEqual 필터는 아직 지원하지 않습니다."
            );
        }

        if (precipitationTypeEqual != null
                && !precipitationTypeEqual.isBlank()) {
            throw new IllegalArgumentException(
                    "precipitationTypeEqual 필터는 아직 지원하지 않습니다."
            );
        }

        FeedPageResponse response = feedService.getFeedPage(
                cursor,
                idAfter,
                limit,
                sortBy,
                sortDirection,
                keywordLike,
                authorIdEqual
        );

        return ResponseEntity.ok(response);
    }

    // 피드 생성
    @PostMapping
    public ResponseEntity<FeedResponse> createFeed(
            @Valid @RequestBody CreateFeedRequest request,
            Authentication authentication
    ) {

        UUID currentUserId = getCurrentUserId(authentication);

        UUID feedId = feedService.createFeed(
                request,
                currentUserId
        );

        FeedResponse response = feedService.getFeedResponse(feedId);

        URI location = URI.create("/api/feeds/" + feedId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    // 피드 수정
    @PatchMapping("/{feedId}")
    public ResponseEntity<FeedResponse> updateFeed(
            @PathVariable UUID feedId,
            @Valid @RequestBody UpdateFeedRequest request,
            Authentication authentication
    ) {

        UUID currentUserId = getCurrentUserId(authentication);

        feedService.updateFeed(
                feedId,
                request,
                currentUserId
        );

        FeedResponse response = feedService.getFeedResponse(feedId);

        return ResponseEntity.ok(response);
    }

    // 피드 삭제
    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> deleteFeed(
            @PathVariable UUID feedId,
            Authentication authentication
    ) {

        UUID currentUserId = getCurrentUserId(authentication);

        feedService.deleteFeed(feedId, currentUserId);

        return ResponseEntity.noContent().build();
    }

    // JWT에서 로그인 사용자 UUID 추출
    private UUID getCurrentUserId(Authentication authentication) {

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        try {
            return UUID.fromString(jwtAuth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException(
                    "유효하지 않은 사용자 인증 정보입니다."
            );
        }
    }
}
