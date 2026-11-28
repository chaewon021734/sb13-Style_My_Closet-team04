
-- V4: follows 테이블 생성

CREATE TABLE follows (
                         id UUID PRIMARY KEY,

                         follower_id UUID NOT NULL,
                         following_id UUID NOT NULL,

                         created_at TIMESTAMPTZ NOT NULL,
                         updated_at TIMESTAMPTZ NOT NULL,

                         CONSTRAINT uk_follows_follower_following
                             UNIQUE (follower_id, following_id),

                         CONSTRAINT chk_follows_not_self
                             CHECK (follower_id <> following_id)
);

-- 팔로잉 목록 조회용 인덱스
CREATE INDEX idx_follows_follower_created
    ON follows (follower_id, created_at DESC, id DESC);

-- 팔로워 목록 조회용 인덱스
CREATE INDEX idx_follows_following_created
    ON follows (following_id, created_at DESC, id DESC);
