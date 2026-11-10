-- 재로그인 시 기존 JWT를 무효화하기 위한 로그인 버전 컬럼 추가

ALTER TABLE users
    ADD COLUMN login_version BIGINT NOT NULL DEFAULT 0;
