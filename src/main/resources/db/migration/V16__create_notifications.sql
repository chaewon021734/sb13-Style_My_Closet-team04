-- 사용자 알림
CREATE TABLE notifications (
    id UUID NOT NULL,
    receiver_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500) NOT NULL,
    level VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_receiver
        FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 수신자별 최신순 커서 조회
CREATE INDEX idx_notifications_receiver_created_at
    ON notifications (receiver_id, created_at DESC, id DESC);
