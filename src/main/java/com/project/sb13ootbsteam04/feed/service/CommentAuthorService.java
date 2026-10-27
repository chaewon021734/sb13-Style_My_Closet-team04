
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.feed.dto.CommentResponse;
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
public class CommentAuthorService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    // 댓글 작성자 정보 조회
    public CommentResponse.AuthorDto getAuthor(UUID userId) {

        // 1. 작성자 ID 검증
        if (userId == null) {
            throw new IllegalArgumentException(
                    "댓글 작성자 ID는 필수입니다."
            );
        }

        // 2. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다: " + userId
                        ));

        // 3. 프로필 이미지 조회
        String profileImageUrl = profileRepository.findById(userId)
                .map(Profile::getProfileImageUrl)
                .orElse(null);

        // 4. 댓글 작성자 DTO 생성
        return new CommentResponse.AuthorDto(
                user.getId(),
                user.getName(),
                profileImageUrl
        );
    }
}
