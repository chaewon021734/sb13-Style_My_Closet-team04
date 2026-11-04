
package com.project.sb13ootbsteam04.follow.controller;

import com.project.sb13ootbsteam04.follow.dto.CreateFollowRequest;
import com.project.sb13ootbsteam04.follow.dto.FollowPageResponse;
import com.project.sb13ootbsteam04.follow.dto.FollowResponse;
import com.project.sb13ootbsteam04.follow.dto.FollowSummaryResponse;
import com.project.sb13ootbsteam04.follow.service.FollowService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
@Validated
public class FollowController {

    private final FollowService followService;

    // 팔로우 생성
    // POST /api/follows
    @PostMapping
    public ResponseEntity<FollowResponse> createFollow(
            @Valid @RequestBody CreateFollowRequest request,
            Authentication authentication
    ) {
        UUID currentUserId = getCurrentUserId(authentication);

        // 다른 사용자 계정으로 팔로우하는 행위 방지
        if (!currentUserId.equals(request.followerId())) {
            throw new AccessDeniedException(
                    "본인의 계정으로만 팔로우할 수 있습니다."
            );
        }

        FollowResponse response =
                followService.createFollowResponse(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 팔로우 취소
    // DELETE /api/follows/{followId}
    @DeleteMapping("/{followId}")
    public ResponseEntity<Void> deleteFollow(
            @PathVariable UUID followId,
            Authentication authentication
    ) {
        UUID currentUserId = getCurrentUserId(authentication);

        followService.deleteFollow(followId, currentUserId);

        return ResponseEntity.noContent().build();
    }

    // 팔로우 요약 정보 조회
    // GET /api/follows/summary?userId={userId}
    @GetMapping("/summary")
    public ResponseEntity<FollowSummaryResponse> getFollowSummary(
            @RequestParam UUID userId,
            Authentication authentication
    ) {
        UUID currentUserId = getCurrentUserId(authentication);

        FollowSummaryResponse response =
                followService.getFollowSummary(
                        userId,
                        currentUserId
                );

        return ResponseEntity.ok(response);
    }

    // 팔로잉 목록 조회
    // GET /api/follows/followings
    @GetMapping("/followings")
    public ResponseEntity<FollowPageResponse> getFollowings(
            @RequestParam UUID followerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String nameLike
    ) {
        FollowPageResponse response =
                followService.getFollowingPage(
                        followerId,
                        cursor,
                        idAfter,
                        limit,
                        nameLike
                );

        return ResponseEntity.ok(response);
    }

    // 팔로워 목록 조회
    // GET /api/follows/followers
    @GetMapping("/followers")
    public ResponseEntity<FollowPageResponse> getFollowers(
            @RequestParam UUID followeeId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String nameLike
    ) {
        FollowPageResponse response =
                followService.getFollowerPage(
                        followeeId,
                        cursor,
                        idAfter,
                        limit,
                        nameLike
                );

        return ResponseEntity.ok(response);
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
