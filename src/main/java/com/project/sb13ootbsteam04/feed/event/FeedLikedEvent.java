package com.project.sb13ootbsteam04.feed.event;

import java.util.UUID;

// 좋아요가 새로 저장된 뒤 피드 작성자 알림에 사용한다.
public record FeedLikedEvent(UUID feedId, UUID feedAuthorId, UUID likerId) {
}
