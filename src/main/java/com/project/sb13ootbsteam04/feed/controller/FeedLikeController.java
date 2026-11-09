
package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.service.FeedLikeService;
import com.project.sb13ootbsteam04.global.security.JwtUserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
public class FeedLikeController {

    private final FeedLikeService feedLikeService;
    private final JwtUserIdResolver jwtUserIdResolver;

    // 피드 좋아요 등록
    @PostMapping("/{feedId}/like")
    public ResponseEntity<Void> likeFeed(
            @PathVariable UUID feedId,
            Authentication authentication
    ) {

        // 공통 JWT 인증 처리 클래스로 사용자 ID 추출
        UUID currentUserId = jwtUserIdResolver.resolve(authentication);

        feedLikeService.likeFeed(feedId, currentUserId);

        return ResponseEntity.noContent().build();
    }

    // 피드 좋아요 취소
    @DeleteMapping("/{feedId}/like")
    public ResponseEntity<Void> unlikeFeed(
            @PathVariable UUID feedId,
            Authentication authentication
    ) {

        // 공통 JWT 인증 처리 클래스로 사용자 ID 추출
        UUID currentUserId = jwtUserIdResolver.resolve(authentication);

        feedLikeService.unlikeFeed(feedId, currentUserId);

        return ResponseEntity.noContent().build();
    }
}
