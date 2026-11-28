package com.project.sb13ootbsteam04.directmessage.event;

import com.project.sb13ootbsteam04.directmessage.dto.response.DirectMessageResponse;

// DM이 저장됐다 (커밋 후 실시간 전달·수신자 알림)
public record DirectMessageSentEvent(DirectMessageResponse message) {
}
