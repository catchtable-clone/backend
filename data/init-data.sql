CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    nickname VARCHAR(255) NOT NULL UNIQUE,
    profile_image VARCHAR(255),
    google_id VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    noshow_count INTEGER NOT NULL,
    noshow_restricted_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    is_deleted BOOLEAN NOT NULL
);

INSERT INTO users (email, nickname, google_id, role, status, noshow_count, is_deleted, created_at)
VALUES ('admin@test.com', '관리자', 'google-admin-1', 'ADMIN', 'ACTIVE', 0, false, NOW()),
       ('user1@test.com', '일반유저1', 'google-user-1', 'USER', 'ACTIVE', 0, false, NOW()),
       ('user2@test.com', '일반유저2', 'google-user-2', 'USER', 'ACTIVE', 0, false, NOW());

CREATE TABLE IF NOT EXISTS bookmark_folders (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    folder_name VARCHAR(255) NOT NULL,
    folder_type VARCHAR(255) NOT NULL,
    color       VARCHAR(20)  NOT NULL DEFAULT '#F97316',
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    is_deleted  BOOLEAN      NOT NULL DEFAULT false
);

INSERT INTO bookmark_folders (user_id, folder_name, folder_type, color, is_deleted, created_at)
SELECT id, '기본 폴더', 'DEFAULT', '#F97316', false, NOW()
FROM users;

CREATE TABLE IF NOT EXISTS stores (
    id BIGSERIAL PRIMARY KEY,
    store_name VARCHAR(255) NOT NULL,
    store_image VARCHAR(255),
    category VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    address VARCHAR(255) NOT NULL,
    district VARCHAR(255) NOT NULL,
    team INTEGER NOT NULL,
    open_time VARCHAR(255) NOT NULL,
    close_time VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    review_count INTEGER NOT NULL,
    bookmark_count INTEGER NOT NULL,
    average_star DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    is_deleted BOOLEAN NOT NULL
);

DROP TABLE IF EXISTS temp_stores;

CREATE TEMP TABLE temp_stores (
                                  store_name text, store_image text, category text,
                                  latitude double precision, longitude double precision,
                                  address text, district text, team integer,
                                  open_time text, close_time text
);

COPY temp_stores FROM '/tmp/data/store_data.csv' WITH (FORMAT csv, HEADER true);

INSERT INTO stores (store_name, store_image, category, latitude, longitude, address, district, team, open_time,
                    close_time, status, review_count, bookmark_count, average_star, is_deleted, created_at)
SELECT store_name,
       NULLIF(store_image, ''),
       category,
       latitude,
       longitude,
       address,
       district,
       team,
       open_time,
       close_time,
       'ACTIVE',
       0,
       0,
       0.0,
       false,
       NOW()
FROM temp_stores;


-- 1. 쿠폰 템플릿 생성 (AI 테스트용 5000원 할인 쿠폰)
INSERT INTO coupon_templates (id, coupon_name, amount, discount_rate, started_at, expired_at, remain, created_at, updated_at, is_deleted)
VALUES (100, 'AI 테스트용 쿠폰', 5000, 10, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 100, NOW(), NOW(), false)
ON CONFLICT (id) DO NOTHING; -- 이미 100번 템플릿이 있으면 무시

-- 2. 4번 사용자에게 위 템플릿으로 쿠폰 발급
INSERT INTO coupons (user_id, coupon_template_id, status, created_at, updated_at, is_deleted)
VALUES (4, 100, 'UNUSED', NOW(), NOW(), false)
ON CONFLICT DO NOTHING; -- 이미 해당 유저가 이 쿠폰을 가지고 있으면 무시

-- (PostgreSQL 사용 시) 시퀀스 값 업데이트
SELECT setval('coupon_templates_id_seq', (SELECT MAX(id) FROM coupon_templates));
SELECT setval('coupons_id_seq', (SELECT MAX(id) FROM coupons));

