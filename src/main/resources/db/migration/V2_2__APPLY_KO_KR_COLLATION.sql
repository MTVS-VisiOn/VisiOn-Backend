CREATE COLLATION IF NOT EXISTS ko_kr (provider = icu, locale = 'ko-KR');

ALTER TABLE favorites
ALTER COLUMN name TYPE VARCHAR(50) COLLATE ko_kr;

ALTER TABLE favorites
ALTER COLUMN nickname TYPE VARCHAR(50) COLLATE ko_kr;