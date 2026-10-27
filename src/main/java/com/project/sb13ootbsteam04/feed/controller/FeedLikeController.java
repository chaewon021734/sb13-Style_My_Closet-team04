
package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.service.FeedLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
public class FeedLikeController {

    private final FeedLikeService feedLikeService;

    // 피드 좋아요 등록
    @PostMapping("/{feedId}/like")
    public ResponseEntity<Void> likeFeed(
            @PathVariable UUID feedId
    ) {
        // TODO: 인증 기능 연동 후 로그인 사용자 ID 조회
        UUID userId = getCurrentUserId();

        feedLikeService.likeFeed(feedId, userId);

        return ResponseEntity.noContent().build();
    }

    // 피드 좋아요 취소
    @DeleteMapping("/{feedId}/like")
    public ResponseEntity<Void> unlikeFeed(
            @PathVariable UUID feedId
    ) {
        // TODO: 인증 기능 연동 후 로그인 사용자 ID 조회
        UUID userId = getCurrentUserId();

        feedLikeService.unlikeFeed(feedId, userId);

        return ResponseEntity.noContent().build();
    }

    // 인증 기능 연동 전 임시 메서드
    private UUID getCurrentUserId() {
        throw new UnsupportedOperationException(
                "로그인 사용자 조회 기능은 인증 모듈 연동 후 구현해야 합니다."
        );
    }
}
