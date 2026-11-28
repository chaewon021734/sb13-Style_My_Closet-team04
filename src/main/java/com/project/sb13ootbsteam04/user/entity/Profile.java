package com.project.sb13ootbsteam04.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "location", columnDefinition = "jsonb")
    private Map<String, Object> location;

    @Column(name = "temperature_sensitivity")
    private Integer temperatureSensitivity;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    // 프로필 첫 수정 시 생성
    public Profile(User user) {
        this.user = user;
    }

    // 보낸 값만 변경한다 (null은 기존 값 유지)
    public void update(String gender, LocalDate birthDate, Map<String, Object> location,
                       Integer temperatureSensitivity) {
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (location != null) {
            this.location = location;
        }
        if (temperatureSensitivity != null) {
            this.temperatureSensitivity = temperatureSensitivity;
        }
    }

    public void changeProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
