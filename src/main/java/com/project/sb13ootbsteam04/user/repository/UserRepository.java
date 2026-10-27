package com.project.sb13ootbsteam04.user.repository;

import com.project.sb13ootbsteam04.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends
        JpaRepository<User, UUID>,
        JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
