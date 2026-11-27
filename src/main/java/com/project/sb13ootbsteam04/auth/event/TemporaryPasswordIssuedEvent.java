package com.project.sb13ootbsteam04.auth.event;

/**
 * 임시 비밀번호 발급 후 이메일 발송을 위한 이벤트.
 *
 * 평문 비밀번호와 해시가 포함되어 있으므로
 * 로그에 출력하거나 영구 저장하지 않는다.
 */
public record TemporaryPasswordIssuedEvent(
        String email,
        String temporaryPassword,
        String temporaryPasswordHash,
        long loginVersion
) {
}
