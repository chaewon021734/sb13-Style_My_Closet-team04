package com.project.sb13ootbsteam04.auth.entity;

public enum RefreshTokenRevocationReason {

    // Refresh Token Rotation으로 인한 폐기
    ROTATED,

    // 사용자 로그아웃으로 인한 폐기
    LOGOUT,

    // 일반적인 Refresh Token 폐기
    REVOKED
}