
package com.project.sb13ootbsteam04.follow.controller;

import com.project.sb13ootbsteam04.follow.dto.CreateFollowRequest;
import com.project.sb13ootbsteam04.follow.dto.FollowPageResponse;
import com.project.sb13ootbsteam04.follow.dto.FollowResponse;
import com.project.sb13ootbsteam04.follow.dto.FollowSummaryResponse;
import com.project.sb13ootbsteam04.follow.service.FollowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
            @Valid @RequestBody CreateFollowRequest request
    ) {

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
            @PathVariable UUID followId
    ) {

        followService.deleteFollow(followId);

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
            @RequestParam @Min(1) int limit,
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
            @RequestParam @Min(1) int limit,
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

    // 로그인 사용자 ID 조회
    // 팀원 인증 모듈 연동 후 실제 구현 예정
    private UUID getCurrentUserId(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        // 현재 인증 principal 구조가 확정되지 않았으므로
        // 사용자 UUID를 임의로 추측하지 않는다.
        return null;
    }
}
