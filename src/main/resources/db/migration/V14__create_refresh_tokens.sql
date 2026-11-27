-- Refresh Token 저장 테이블 생성
-- 보안을 위해 토큰 원문이 아닌 SHA-256 해시값 저장

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    login_version BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_refresh_tokens_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT chk_refresh_tokens_expiration
        CHECK (expires_at > created_at)
);

-- 사용자별 Refresh Token 조회
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

-- 만료된 Refresh Token 조회 및 정리
CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);