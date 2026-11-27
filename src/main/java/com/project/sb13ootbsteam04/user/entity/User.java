package com.project.sb13ootbsteam04.user.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    // 로그인 성공 시 증가하는 버전
    // 이전에 발급한 JWT를 무효화하기 위해 사용
    @Column(name = "login_version", nullable = false)
    private long loginVersion = 0L;

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = UserRole.USER;
        this.locked = false;
        this.loginVersion = 0L;
    }

    // ========================================
    // 1. 로그인 버전 증가 (PR A)
    // ========================================

    // 로그인 성공 시 버전 증가
    // JWT에 포함된 버전과 DB의 버전이 다르면 인증 거부
    public void increaseLoginVersion() {
        this.loginVersion++;
    }

    // ========================================
    // 2. 초기 관리자 계정 생성 (PR B)
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

        // 기존 권한과 동일하면 변경하지 않음
        if (this.role == newRole) {
            return;
        }

        this.role = newRole;

        // 권한이 변경되면 기존 JWT 무효화
        increaseLoginVersion();
    }

    // ========================================
    // 4. 사용자 계정 잠금 및 해제 (PR B)
    // ========================================

    public void changeLocked(boolean locked) {

        // 현재 상태와 동일하면 변경하지 않음
        if (this.locked == locked) {
            return;
        }

        this.locked = locked;

        // 계정 잠금 및 해제 시 기존 JWT 무효화
        increaseLoginVersion();
    }
}
