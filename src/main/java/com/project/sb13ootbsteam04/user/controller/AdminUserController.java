package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.service.AdminUserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    // ========================================
    // 1. 사용자 권한 변경
    // ========================================

    @PatchMapping("/{userId}/role")
    public ResponseEntity<Void> changeRole(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeRoleRequest request
    ) {

        adminUserService.changeRole(
                userId,
                request.role()
        );

        return ResponseEntity.noContent().build();
    }

    // ========================================
    // 2. 사용자 계정 잠금 및 해제
    // ========================================

    @PatchMapping("/{userId}/locked")
    public ResponseEntity<Void> changeLocked(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeLockedRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // 현재 인증된 관리자 ID
        UUID currentAdminId = UUID.fromString(
                jwt.getSubject()
        );

        adminUserService.changeLocked(
                userId,
                request.locked(),
                currentAdminId
        );

        return ResponseEntity.noContent().build();
    }

    // ========================================
    // 요청 DTO
    // ========================================

    public record ChangeRoleRequest(

            @NotNull(message = "변경할 권한은 필수입니다.")
            UserRole role

    ) {
    }

    public record ChangeLockedRequest(

            @NotNull(message = "잠금 상태는 필수입니다.")
            Boolean locked

    ) {
    }
}
