package com.project.sb13ootbsteam04.user.dto.response;

import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record ProfileDto(
    UUID userId,
    String name,
    String gender,
    LocalDate birthDate,
    Map<String, Object> location,
    Integer temperatureSensitivity,
    String profileImageUrl
) {

  // 프로필을 아직 만들지 않았으면 이름만 채운다
  public static ProfileDto of(User user, Profile profile) {
    if (profile == null) {
      return new ProfileDto(user.getId(), user.getName(), null, null, null, null, null);
    }
    return new ProfileDto(
        user.getId(),
        user.getName(),
        profile.getGender(),
        profile.getBirthDate(),
        profile.getLocation(),
        profile.getTemperatureSensitivity(),
        profile.getProfileImageUrl()
    );
  }
}
