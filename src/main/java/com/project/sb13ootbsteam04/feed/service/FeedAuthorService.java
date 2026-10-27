
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.dto.FeedResponse;
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
public class FeedAuthorService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    // 피드 작성자 정보 조회
    public FeedResponse.AuthorDto getAuthor(UUID userId) {

        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다: " + userId
                        ));

        // 2. 프로필 이미지 조회
        String profileImageUrl = profileRepository.findById(userId)
                .map(Profile::getProfileImageUrl)
                .orElse(null);

        // 3. 피드 작성자 DTO 생성
        return new FeedResponse.AuthorDto(
                user.getId(),
                user.getName(),
                profileImageUrl
        );
    }
}
