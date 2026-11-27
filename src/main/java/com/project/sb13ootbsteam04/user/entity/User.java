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

    // 로그인 성공 시 버전 증가
    // JWT에 포함된 버전과 DB의 버전이 다르면 인증 거부
    public void increaseLoginVersion() {
        this.loginVersion++;
    }
}
