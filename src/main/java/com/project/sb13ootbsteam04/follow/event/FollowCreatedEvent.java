package com.project.sb13ootbsteam04.follow.event;

import java.util.UUID;

// 팔로우가 저장된 뒤 팔로우 대상 알림에 사용한다.
public record FollowCreatedEvent(UUID followerId, UUID followeeId) {
}
