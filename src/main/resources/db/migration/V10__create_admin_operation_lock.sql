-- 관리자 권한 변경 및 계정 잠금 작업의 동시성 제어

CREATE TABLE admin_operation_locks (
    id INTEGER PRIMARY KEY
);

-- 모든 관리자 변경 작업이 공유하는 고정 잠금 행
INSERT INTO admin_operation_locks (id)
VALUES (1);
