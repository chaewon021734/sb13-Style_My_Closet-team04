package com.project.sb13ootbsteam04.user.event;

import com.project.sb13ootbsteam04.user.entity.UserRole;

import java.util.UUID;

// 권한이 실제로 바뀐 뒤 대상 사용자 알림에 사용한다.
public record UserRoleChangedEvent(UUID userId, UserRole newRole) {
}
