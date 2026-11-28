-- 강수 확률을 기상청 값 그대로(0~100) 저장하도록 바꾸면서, 기존에 100으로 나눠 저장한 값을 되돌린다.
UPDATE weathers
SET precipitation_probability = precipitation_probability * 100
WHERE precipitation_probability IS NOT NULL;
