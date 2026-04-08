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
