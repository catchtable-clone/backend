-- geometry → geography 타입 변환 마이그레이션
-- 근거: geometry 컬럼에 ::geography 캐스팅 시 GIST 인덱스를 타지 못해 Full Table Scan 발생
--       geography 타입으로 변경하면 캐스팅 없이 인덱스 직접 활용 가능
-- 실행: docker exec -i catchtable-db psql -U $DB_USER -d $DB_NAME < data/migration_v3_geometry_to_geography.sql

-- 1. 기존 GIST 인덱스 삭제 (컬럼 타입 변경 전에 제거 필요)
DROP INDEX IF EXISTS idx_stores_location_gist;

-- 2. geometry 컬럼을 geography 타입으로 변환
ALTER TABLE stores
    ALTER COLUMN location TYPE geography(Point, 4326)
    USING location::geography;

-- 3. geography 컬럼에 GIST 인덱스 재생성
CREATE INDEX IF NOT EXISTS idx_stores_location_gist
    ON stores USING GIST (location);

-- 검증
-- SELECT pg_typeof(location) FROM stores LIMIT 1; -- geography(Point,4326) 이어야 정상
-- SELECT indexname FROM pg_indexes WHERE tablename = 'stores' AND indexname = 'idx_stores_location_gist';
