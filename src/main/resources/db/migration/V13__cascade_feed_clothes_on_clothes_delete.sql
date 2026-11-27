-- 옷 삭제는 허용하고, 해당 옷의 피드 연결만 같은 트랜잭션에서 제거한다.
-- 옷 삭제 시 해당 피드 연결만 찾을 수 있도록 의상 ID를 인덱싱한다.
CREATE INDEX idx_feed_clothes_clothes_id ON feed_clothes (clothes_id);

-- 새 연결에는 외래 키를 적용하고, 이미 저장된 피드–의상 연결은 고아 행을 정리한 뒤 검증한다.
ALTER TABLE feed_clothes ADD CONSTRAINT fk_feed_clothes_clothes
    FOREIGN KEY (clothes_id) REFERENCES clothes (id) ON DELETE CASCADE NOT VALID;

DELETE FROM feed_clothes fc
WHERE NOT EXISTS (SELECT 1 FROM clothes c WHERE c.id = fc.clothes_id);

ALTER TABLE feed_clothes VALIDATE CONSTRAINT fk_feed_clothes_clothes;
