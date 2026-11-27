-- Refresh Token 폐기 사유 컬럼 추가
ALTER TABLE refresh_tokens
    ADD COLUMN revocation_reason VARCHAR(20);

-- 기존 폐기 토큰의 폐기 사유 초기화
UPDATE refresh_tokens
SET revocation_reason = 'REVOKED'
WHERE revoked_at IS NOT NULL;

-- 허용된 폐기 사유 검증
ALTER TABLE refresh_tokens
    ADD CONSTRAINT chk_refresh_tokens_revocation_reason
    CHECK (
        revocation_reason IN (
            'ROTATED',
            'LOGOUT',
            'REVOKED'
        )
    );

-- 폐기 시간과 폐기 사유의 일관성 보장
ALTER TABLE refresh_tokens
    ADD CONSTRAINT chk_refresh_tokens_revocation_state
    CHECK (
        (
            revoked_at IS NULL
            AND revocation_reason IS NULL
        )
        OR
        (
            revoked_at IS NOT NULL
            AND revocation_reason IS NOT NULL
        )
    );