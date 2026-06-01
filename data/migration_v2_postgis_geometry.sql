-- PostGIS geometry 컬럼 추가 + GIST 인덱스 마이그레이션
-- 실행 방법: docker exec -i catchtable-db psql -U $DB_USER -d $DB_NAME < data/migration_v2_postgis_geometry.sql
-- 주의: ddl-auto를 validate로 변경하기 전에 반드시 이 스크립트를 먼저 실행해야 함

-- 1. PostGIS 확장 활성화 (이미 활성화된 경우 무시)
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. stores 테이블에 geometry 컬럼 추가
ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS location geometry(Point, 4326);

-- 3. 기존 latitude/longitude 데이터를 geometry 컬럼으로 복사
UPDATE stores
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
WHERE location IS NULL
  AND latitude IS NOT NULL
  AND longitude IS NOT NULL;

-- 4. GIST 공간 인덱스 생성 (B-tree @Index로는 생성 불가 — 반드시 SQL로 직접 생성)
CREATE INDEX IF NOT EXISTS idx_stores_location_gist
    ON stores USING GIST (location);

-- 5. 기존 nearby 성능 개선용 복합 B-tree 인덱스 (바운딩박스 선필터용)
CREATE INDEX IF NOT EXISTS idx_stores_lat_lng
    ON stores (latitude, longitude)
    WHERE is_deleted = false;

-- 검증 쿼리
-- SELECT COUNT(*) FROM stores WHERE location IS NULL; -- 0이어야 정상
-- SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'stores' AND indexname LIKE 'idx_stores_%';
