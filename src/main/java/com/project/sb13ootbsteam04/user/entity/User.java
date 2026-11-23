package com.project.sb13ootbsteam04.user.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean locked = false;

    // 로그인 성공 및 비밀번호 변경 시 증가하는 버전
    @Column(nullable = false)
    private long loginVersion = 0L;

    // 암호화된 임시 비밀번호
    @Column(name = "temporary_password_hash", length = 255)
    private String temporaryPasswordHash;

    // 임시 비밀번호 만료 시간
    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = UserRole.USER;
        this.locked = false;
        this.loginVersion = 0L;
    }

    // 초기 관리자 계정 생성 전용
    public static User createAdmin(
            String name,
            String email,
            String encodedPassword
    ) {
        User admin = new User(name, email, encodedPassword);
        admin.role = UserRole.ADMIN;
        return admin;
    }

    // 관리자에 의한 사용자 권한 변경
    public void changeRole(UserRole newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException(
                    "변경할 권한은 필수입니다."
            );
        }

        this.role = newRole;
    }

    // 관리자에 의한 계정 잠금 및 해제
    public void changeLocked(boolean locked) {
        this.locked = locked;
    }

    // 로그인 성공 및 기존 JWT 무효화 시 버전 증가
    public void increaseLoginVersion() {
        this.loginVersion++;
    }

    // ========================================
    // 임시 비밀번호 관리
    // ========================================

    // 암호화된 임시 비밀번호와 만료 시간 저장
    public void issueTemporaryPassword(
            String encodedPassword,
            Instant expiresAt
    ) {

        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "임시 비밀번호는 필수입니다."
            );
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "임시 비밀번호 만료 시간은 필수입니다."
            );
        }

        // 1. 임시 비밀번호 정보 갱신
        this.temporaryPasswordHash = encodedPassword;
        this.temporaryPasswordExpiresAt = expiresAt;

        // 2. 기존 JWT 무효화
        // 최초 발급 및 재발급 시 모두 로그인 버전 증가
        increaseLoginVersion();
    }

    // 임시 비밀번호 사용 가능 시간 확인
    public boolean isTemporaryPasswordActive(Instant now) {

        if (now == null) {
            throw new IllegalArgumentException(
                    "현재 시간은 필수입니다."
            );
        }

        return temporaryPasswordHash != null
                && temporaryPasswordExpiresAt != null
                && now.isBefore(temporaryPasswordExpiresAt);
    }

    // 임시 비밀번호 정보 삭제
    public void clearTemporaryPassword() {
        this.temporaryPasswordHash = null;
        this.temporaryPasswordExpiresAt = null;
    }

    // 새 비밀번호로 변경하고 임시 비밀번호 삭제
    public void changePassword(String encodedPassword) {

        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "새 비밀번호는 필수입니다."
            );
        }

        // 1. 새 비밀번호 적용
        this.password = encodedPassword;

        // 2. 임시 비밀번호 정보 삭제
        clearTemporaryPassword();

        // 3. 기존 JWT 무효화
        increaseLoginVersion();
    }
}
