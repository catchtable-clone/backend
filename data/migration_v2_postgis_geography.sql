-- PostGIS geography 컬럼 추가 + GIST 인덱스 마이그레이션
-- 실행: docker exec -i catchtable-db psql -U $DB_USER -d $DB_NAME < data/migration_v2_postgis_geography.sql
-- 로컬에서 이미 실행했다면 운영 서버에서만 실행할 것

-- 1. PostGIS 확장 활성화
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. stores 테이블에 geography 컬럼 추가
--    geometry 대신 geography 사용: ::geography 캐스팅 없이 GIST 인덱스 직접 활용 가능
ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS location geography(Point, 4326);

-- 3. 기존 latitude/longitude 데이터를 geography 컬럼으로 복사
UPDATE stores
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
WHERE location IS NULL
  AND latitude IS NOT NULL
  AND longitude IS NOT NULL;

-- 4. GIST 공간 인덱스 생성
--    findNearbyWithGist()의 ST_DWithin + KNN 정렬용. ddl-auto는 geometry/GIST를 못 만들므로
--    이 마이그레이션이 유일한 생성 경로.
CREATE INDEX IF NOT EXISTS idx_stores_location_gist
    ON stores USING GIST (location);

-- NOTE: (latitude, longitude) B-tree 인덱스는 Store 엔티티의 @Index(idx_store_lat_lng) 로 이미
-- ddl-auto 가 생성/유지하므로 여기서 별도 추가하지 않는다. 과거 버전에서 idx_stores_lat_lng
-- 라는 다른 이름으로 중복 생성되던 문제를 방지.

-- 검증
-- SELECT pg_typeof(location) FROM stores LIMIT 1;           -- geography 이어야 정상
-- SELECT COUNT(*) FROM stores WHERE location IS NULL;       -- 0 이어야 정상
-- SELECT indexname FROM pg_indexes WHERE tablename = 'stores' AND indexname LIKE 'idx_stores%';
