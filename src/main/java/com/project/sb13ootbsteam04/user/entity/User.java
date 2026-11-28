package com.project.sb13ootbsteam04.user.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

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

    // 로그인 버전 (PR A)
    @Column(name = "login_version", nullable = false)
    private long loginVersion = 0L;

    // ========================================
    // PR C - 임시 비밀번호 정보
    // ========================================

    @Column(name = "temporary_password_hash", length = 255)
    private String temporaryPasswordHash;

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

    // ========================================
    // 1. 로그인 버전 관리 (PR A)
    // ========================================

    public void increaseLoginVersion() {
        this.loginVersion++;
    }

    // ========================================
    // 2. 초기 관리자 생성 (PR B)
    // ========================================

    public static User createAdmin(
            String name,
            String email,
            String encodedPassword
    ) {
        User admin = new User(name, email, encodedPassword);
        admin.role = UserRole.ADMIN;
        return admin;
    }

    // ========================================
    // 3. 사용자 권한 변경 (PR B)
    // ========================================

    public void changeRole(UserRole newRole) {

        Objects.requireNonNull(
                newRole,
                "변경할 사용자 권한은 필수입니다."
        );

        if (this.role == newRole) {
            return;
        }

        this.role = newRole;
        increaseLoginVersion();
    }

    // ========================================
    // 4. 계정 잠금 및 해제 (PR B)
    // ========================================

    public void changeLocked(boolean locked) {

        if (this.locked == locked) {
            return;
        }

        this.locked = locked;
        increaseLoginVersion();
    }

    // ========================================
    // 5. 임시 비밀번호 발급 (PR C)
    // ========================================

    public void issueTemporaryPassword(
            String encodedPassword,
            Instant expiresAt
    ) {

        if (encodedPassword == null
                || encodedPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "임시 비밀번호는 필수입니다."
            );
        }

        Objects.requireNonNull(
                expiresAt,
                "임시 비밀번호 만료 시간은 필수입니다."
        );

        this.temporaryPasswordHash = encodedPassword;
        this.temporaryPasswordExpiresAt = expiresAt;

        // 기존 JWT 무효화
        increaseLoginVersion();
    }

    // ========================================
    // 6. 임시 비밀번호 유효성 확인 (PR C)
    // ========================================

    public boolean isTemporaryPasswordActive(Instant now) {

        Objects.requireNonNull(
                now,
                "현재 시간은 필수입니다."
        );

        return temporaryPasswordHash != null
                && temporaryPasswordExpiresAt != null
                && now.isBefore(temporaryPasswordExpiresAt);
    }

    // ========================================
    // 7. 임시 비밀번호 정보 삭제 (PR C)
    // ========================================

    public void clearTemporaryPassword() {

        this.temporaryPasswordHash = null;
        this.temporaryPasswordExpiresAt = null;
    }

    // ========================================
    // 8. 새 비밀번호로 변경 (PR C)
    // ========================================

    public void changePassword(String encodedPassword) {

        if (encodedPassword == null
                || encodedPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "새 비밀번호는 필수입니다."
            );
        }

        this.password = encodedPassword;

        // 임시 비밀번호 사용 종료
        clearTemporaryPassword();

        // 기존 JWT 모두 무효화
        increaseLoginVersion();
    }

    // 프로필 수정에서 이름 변경
    public void changeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 비어 있을 수 없습니다.");
        }
        this.name = name;
    }
}
