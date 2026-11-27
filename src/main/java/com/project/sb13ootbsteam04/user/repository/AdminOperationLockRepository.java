package com.project.sb13ootbsteam04.user.repository;

import com.project.sb13ootbsteam04.user.entity.AdminOperationLock;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminOperationLockRepository
        extends JpaRepository<AdminOperationLock, Integer> {

    // 모든 관리자 변경 작업에서 동일한 행(ID=1)에 쓰기 잠금 적용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT l
            FROM AdminOperationLock l
            WHERE l.id = :id
            """)
    Optional<AdminOperationLock> findByIdForUpdate(
            @Param("id") Integer id
    );
}
