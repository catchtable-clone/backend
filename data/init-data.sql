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
