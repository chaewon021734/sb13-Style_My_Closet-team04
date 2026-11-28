package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.clothes.service.dto.ClothesImageCommand;
import com.project.sb13ootbsteam04.clothes.storage.ClothesImageStorage;
import com.project.sb13ootbsteam04.user.dto.request.ProfileUpdateRequest;
import com.project.sb13ootbsteam04.user.dto.response.ProfileDto;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.exception.UserException;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  // 프로필 이미지도 의상 이미지와 같은 S3 저장소를 쓴다
  private final ClothesImageStorage imageStorage;

  public ProfileDto get(UUID userId) {
    User user = findUser(userId);
    return ProfileDto.of(user, profileRepository.findById(userId).orElse(null));
  }

  // 본인 프로필만 수정. 이미지는 형식을 먼저 확인해 잘못되면 아무것도 바꾸지 않는다.
  @Transactional
  public ProfileDto update(UUID loginUserId, UUID userId, ProfileUpdateRequest request, ClothesImageCommand image) {
    if (!userId.equals(loginUserId)) {
      throw UserException.profileForbidden(userId);
    }
    User user = findUser(userId);
    Profile profile = profileRepository.findById(userId)
        .orElseGet(() -> profileRepository.save(new Profile(user)));

    if (request.name() != null) {
      user.changeName(request.name());
    }
    profile.update(
        request.gender() == null ? null : request.gender().name(),
        request.birthDate(),
        toLocationMap(request.location()),
        request.temperatureSensitivity()
    );
    if (image != null) {
      profile.changeProfileImageUrl(imageStorage.upload(image).url());
    }
    return ProfileDto.of(user, profile);
  }

  private User findUser(UUID userId) {
    return userRepository.findById(userId).orElseThrow(() -> UserException.userNotFound(userId));
  }

  // 날씨 알림 등은 location의 x·y 키를 읽는다
  private Map<String, Object> toLocationMap(ProfileUpdateRequest.Location location) {
    if (location == null) {
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("latitude", location.latitude());
    map.put("longitude", location.longitude());
    map.put("x", location.x());
    map.put("y", location.y());
    map.put("locationNames", location.locationNames());
    return map;
  }
}
