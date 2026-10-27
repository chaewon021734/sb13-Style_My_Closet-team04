package com.project.sb13ootbsteam04.user.repository;

import com.project.sb13ootbsteam04.user.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
}
