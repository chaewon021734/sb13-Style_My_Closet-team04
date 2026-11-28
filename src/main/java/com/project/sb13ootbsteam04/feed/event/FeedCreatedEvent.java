package com.project.sb13ootbsteam04.feed.event;

import java.util.UUID;

// 피드가 저장된 뒤 작성자의 팔로워 알림에 사용한다.
public record FeedCreatedEvent(UUID feedId, UUID authorId) {
}
