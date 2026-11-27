-- 기존 URL만으로 원래 저장소 객체를 식별할 수 없는 행은 NULL로 유지한다.
ALTER TABLE clothes ADD COLUMN image_object_key VARCHAR(500);
