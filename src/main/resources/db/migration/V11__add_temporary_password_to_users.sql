-- PR C - 임시 비밀번호 발급 및 만료 정보

ALTER TABLE users
    ADD COLUMN temporary_password_hash VARCHAR(255);

ALTER TABLE users
    ADD COLUMN temporary_password_expires_at TIMESTAMP WITH TIME ZONE;
