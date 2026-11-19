package com.project.sb13ootbsteam04.user.repository;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends
        JpaRepository<User, UUID>,
        JpaSpecificationExecutor<User> {

    // 이메일로 사용자 조회
    Optional<User> findByEmail(String email);

    // 이메일 중복 여부 확인
    boolean existsByEmail(String email);

    // 특정 권한을 가진 사용자 수 조회
    long countByRole(UserRole role);

    // 로그인 시 사용자 행에 쓰기 락 적용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(
            @Param("email") String email
    );
}
