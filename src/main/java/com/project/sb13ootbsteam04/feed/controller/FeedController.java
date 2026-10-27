
package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.dto.CreateFeedRequest;
import com.project.sb13ootbsteam04.feed.dto.FeedPageResponse;
import com.project.sb13ootbsteam04.feed.dto.FeedResponse;
import com.project.sb13ootbsteam04.feed.dto.UpdateFeedRequest;
import com.project.sb13ootbsteam04.feed.service.FeedService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    // GET /api/feeds
    @GetMapping
    public ResponseEntity<FeedPageResponse> getFeeds(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam @Min(1) int limit,
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

    // 피드 등록
    // POST /api/feeds
    @PostMapping
    public ResponseEntity<FeedResponse> createFeed(
            @Valid @RequestBody CreateFeedRequest request
    ) {

        UUID feedId = feedService.createFeed(request);

        FeedResponse response = feedService.getFeedResponse(feedId);

        URI location = URI.create("/api/feeds/" + feedId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    // 피드 수정
    // PATCH /api/feeds/{feedId}
    @PatchMapping("/{feedId}")
    public ResponseEntity<FeedResponse> updateFeed(
            @PathVariable UUID feedId,
            @Valid @RequestBody UpdateFeedRequest request
    ) {

        // 피드 내용 수정
        feedService.updateFeed(feedId, request);

        // 수정된 피드 정보 조회
        FeedResponse response = feedService.getFeedResponse(feedId);

        return ResponseEntity.ok(response);
    }

    // 피드 삭제
    // DELETE /api/feeds/{feedId}
    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> deleteFeed(
            @PathVariable UUID feedId
    ) {

        feedService.deleteFeed(feedId);

        return ResponseEntity.noContent().build();
    }
}
