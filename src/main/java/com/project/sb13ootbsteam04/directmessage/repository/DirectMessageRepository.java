package com.project.sb13ootbsteam04.directmessage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.project.sb13ootbsteam04.directmessage.entity.DirectMessage;

import java.util.UUID;

public interface DirectMessageRepository
    extends JpaRepository<DirectMessage, UUID> {
}