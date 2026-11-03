
package com.project.sb13ootbsteam04.feed.controller;

import com.project.sb13ootbsteam04.feed.service.FeedLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
            @PathVariable UUID feedId,
            Authentication authentication
    ) {

        UUID currentUserId = getCurrentUserId(authentication);

        feedLikeService.likeFeed(feedId, currentUserId);

        return ResponseEntity.noContent().build();
    }

    // 피드 좋아요 취소
    @DeleteMapping("/{feedId}/like")
    public ResponseEntity<Void> unlikeFeed(
            @PathVariable UUID feedId,
            Authentication authentication
    ) {

        UUID currentUserId = getCurrentUserId(authentication);

        feedLikeService.unlikeFeed(feedId, currentUserId);

        return ResponseEntity.noContent().build();
    }

    // JWT에서 로그인 사용자 UUID 추출
    private UUID getCurrentUserId(Authentication authentication) {

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "로그인이 필요합니다."
            );
        }

        try {
            return UUID.fromString(
                    jwtAuth.getToken().getSubject()
            );
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException(
                    "유효하지 않은 사용자 인증 정보입니다."
            );
        }
    }
}
