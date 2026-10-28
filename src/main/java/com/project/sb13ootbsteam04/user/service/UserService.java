
package com.project.sb13ootbsteam04.user.service;

import com.project.sb13ootbsteam04.user.dto.request.UserCreateRequest;
import com.project.sb13ootbsteam04.user.dto.request.UserSearchRequest;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;
import com.project.sb13ootbsteam04.user.dto.response.UserDtoCursorResponse;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.exception.DuplicateEmailException;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import com.project.sb13ootbsteam04.user.repository.UserSpecification;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    @Transactional
    public UserDto create(UserCreateRequest request) {

        // 1. 이메일 중복 확인
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }

        // 2. 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(request.password());

        // 3. 사용자 생성
        User user = new User(
                request.name(),
                request.email(),
                encodedPassword
        );

        // 4. 데이터베이스 저장
        User savedUser = userRepository.saveAndFlush(user);

        // 5. 사용자 DTO 반환
        return UserDto.from(savedUser);
    }

    // 사용자 목록 조회 (커서 기반 페이지네이션)
    @Transactional(readOnly = true)
    public UserDtoCursorResponse searchUsers(UserSearchRequest request) {

        // 1. 조회 개수 검증 (1~100)
        if (request.limit() == null
                || request.limit() < 1
                || request.limit() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "limit은 1 이상 100 이하여야 합니다."
            );
        }

        // 2. 정렬 방향 검증
        String sortDirection = request.sortDirection();

        if (sortDirection == null) {
            throw new IllegalArgumentException(
                    "정렬 방향은 필수입니다."
            );
        }

        Sort.Direction direction = switch (sortDirection) {
            case "ASCENDING" -> Sort.Direction.ASC;
            case "DESCENDING" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException(
                    "잘못된 정렬 방향입니다."
            );
        };

        // 3. 정렬 기준 검증
        String sortBy = request.sortBy();

        if (!"email".equals(sortBy)
                && !"createdAt".equals(sortBy)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 정렬 기준입니다."
            );
        }

        // 4. 커서 유효성 검사
        boolean hasCursor = request.cursor() != null
                && !request.cursor().isBlank();

        if (hasCursor && request.idAfter() == null) {
            throw new IllegalArgumentException(
                    "cursor 사용 시 idAfter가 필요합니다."
            );
        }

        if (!hasCursor && request.idAfter() != null) {
            throw new IllegalArgumentException(
                    "idAfter 사용 시 cursor가 필요합니다."
            );
        }

        // 5. 기본 필터 조건
        Specification<User> filterSpec = Specification
                .where(UserSpecification.emailLike(request.emailLike()))
                .and(UserSpecification.roleEqual(request.roleEqual()))
                .and(UserSpecification.lockedEqual(request.locked()));

        // 6. 전체 검색 결과 개수
        // cursor 조건은 제외
        long totalCount = userRepository.count(filterSpec);

        // 7. 커서 검색 조건 추가
        Specification<User> searchSpec = filterSpec.and(
                UserSpecification.cursorAfter(
                        request.cursor(),
                        request.idAfter(),
                        sortBy,
                        direction
                )
        );

        // 8. 정렬 기준 및 UUID 보조 정렬
        Sort sort = Sort.by(
                new Sort.Order(direction, sortBy),
                new Sort.Order(direction, "id")
        );

        // 9. limit + 1개 조회
        PageRequest pageRequest =
                PageRequest.of(0, request.limit() + 1, sort);

        List<User> users = userRepository
                .findAll(searchSpec, pageRequest)
                .getContent();

        // 10. 다음 페이지 존재 여부
        boolean hasNext = users.size() > request.limit();

        // 11. 실제 반환할 데이터
        List<User> pageUsers = hasNext
                ? users.subList(0, request.limit())
                : users;

        List<UserDto> data = pageUsers.stream()
                .map(UserDto::from)
                .toList();

        // 12. 다음 커서 생성
        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !pageUsers.isEmpty()) {
            User lastUser = pageUsers.get(pageUsers.size() - 1);

            nextCursor = "email".equals(sortBy)
                    ? lastUser.getEmail()
                    : lastUser.getCreatedAt().toString();

            nextIdAfter = lastUser.getId();
        }

        // 13. Swagger 응답 DTO 반환
        return new UserDtoCursorResponse(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                sortBy,
                sortDirection
        );
    }
}
