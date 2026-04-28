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
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    is_deleted  BOOLEAN      NOT NULL DEFAULT false
);

INSERT INTO bookmark_folders (user_id, folder_name, folder_type, is_deleted, created_at)
SELECT id, '기본 폴더', 'DEFAULT', false, NOW()
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

CREATE TEMP TABLE temp_stores (
                                  store_name text, store_image text, category text,
                                  latitude double precision, longitude double precision,
                                  address text, district text, team integer,
                                  open_time text, close_time text
);

COPY temp_stores FROM '/tmp/data/store_data.csv' WITH (FORMAT csv, HEADER true);

INSERT INTO stores (store_name, store_image, category, latitude, longitude, address, district, team, open_time,
                    close_time, status, review_count, bookmark_count, is_deleted, created_at)
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
       false,
       NOW()
FROM temp_stores;
