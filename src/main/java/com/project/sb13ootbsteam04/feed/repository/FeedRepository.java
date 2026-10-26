package com.project.sb13ootbsteam04.feed.repository;

import com.project.sb13ootbsteam04.feed.entity.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedRepository extends JpaRepository<Feed, UUID> {
}