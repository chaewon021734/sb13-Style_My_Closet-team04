
package com.project.sb13ootbsteam04.follow.service;

import com.project.sb13ootbsteam04.follow.dto.FollowResponse;
import com.project.sb13ootbsteam04.follow.entity.Follow;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowResponseService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    // 팔로우 엔티티를 Swagger 응답 DTO로 변환
    public FollowResponse toResponse(Follow follow) {

        if (follow == null) {
            throw new IllegalArgumentException("팔로우 정보는 필수입니다.");
        }

        FollowResponse.UserDto followee =
                getUserDto(follow.getFollowingId());

        FollowResponse.UserDto follower =
                getUserDto(follow.getFollowerId());

        return new FollowResponse(
                follow.getId(),
                followee,
                follower
        );
    }

    // 사용자 이름 및 프로필 이미지 조회
    private FollowResponse.UserDto getUserDto(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다: " + userId
                        ));

        String profileImageUrl = profileRepository.findById(userId)
                .map(Profile::getProfileImageUrl)
                .orElse(null);

        return new FollowResponse.UserDto(
                user.getId(),
                user.getName(),
                profileImageUrl
        );
    }
}
