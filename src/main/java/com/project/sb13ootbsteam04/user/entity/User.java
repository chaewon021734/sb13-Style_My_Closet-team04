package com.project.sb13ootbsteam04.user.entity;

import com.project.sb13ootbsteam04.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    // 로그인할 때마다 증가하는 버전
    @Column(nullable = false)
    private long loginVersion = 0L;

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

    // 로그인 성공 시 로그인 버전 증가
    public void increaseLoginVersion() {
        this.loginVersion++;
    }
}
