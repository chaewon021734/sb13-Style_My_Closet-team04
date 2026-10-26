package com.project.sb13ootbsteam04.user.repository;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public class UserSpecification {

    private UserSpecification() {
    }

    public static Specification<User> emailLike(String emailLike) {
        return (root, query, cb) -> {
            if (emailLike == null || emailLike.isBlank()) {
                return cb.conjunction();
            }

            return cb.like(
                    cb.lower(root.get("email")),
                    "%" + emailLike.toLowerCase() + "%"
            );
        };
    }

    public static Specification<User> roleEqual(String roleEqual) {
        return (root, query, cb) -> {
            if (roleEqual == null || roleEqual.isBlank()) {
                return cb.conjunction();
            }

            UserRole role = UserRole.valueOf(roleEqual);

            return cb.equal(root.get("role"), role);
        };
    }

    public static Specification<User> lockedEqual(Boolean locked) {
        return (root, query, cb) -> {
            if (locked == null) {
                return cb.conjunction();
            }

            return cb.equal(root.get("locked"), locked);
        };
    }

    public static Specification<User> cursorAfter(
            String cursor,
            UUID idAfter,
            String sortBy,
            Sort.Direction direction
    ) {
        return (root, query, cb) -> {

            // 첫 페이지라면 커서 조건 없음
            if (cursor == null || cursor.isBlank()) {
                return cb.conjunction();
            }

            if (idAfter == null) {
                throw new IllegalArgumentException(
                        "커서 조회 시 idAfter가 필요합니다."
                );
            }

            // email 정렬
            if ("email".equals(sortBy)) {
                var field = root.<String>get("email");

                var comparison = direction.isAscending()
                        ? cb.greaterThan(field, cursor)
                        : cb.lessThan(field, cursor);

                var sameValue = cb.and(
                        cb.equal(field, cursor),
                        direction.isAscending()
                                ? cb.greaterThan(root.<UUID>get("id"), idAfter)
                                : cb.lessThan(root.<UUID>get("id"), idAfter)
                );

                return cb.or(comparison, sameValue);
            }

            // createdAt 정렬
            if ("createdAt".equals(sortBy)) {
                Instant createdAt;

                try {
                    createdAt = Instant.parse(cursor);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "올바르지 않은 createdAt 커서입니다.", e
                    );
                }

                var field = root.<Instant>get("createdAt");

                var comparison = direction.isAscending()
                        ? cb.greaterThan(field, createdAt)
                        : cb.lessThan(field, createdAt);

                var sameValue = cb.and(
                        cb.equal(field, createdAt),
                        direction.isAscending()
                                ? cb.greaterThan(root.<UUID>get("id"), idAfter)
                                : cb.lessThan(root.<UUID>get("id"), idAfter)
                );

                return cb.or(comparison, sameValue);
            }

            throw new IllegalArgumentException(
                    "지원하지 않는 정렬 기준입니다."
            );
        };
    }

}
