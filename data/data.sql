
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- store_id 1번 (시간대: 10:00 ~ 19:00 매 정각)
INSERT INTO store_remain (store_id, remain_date, remain_time, remain_team, version, created_at, updated_at, is_deleted) VALUES
(1, '2026-01-01', '10:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '11:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '12:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '13:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '14:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '15:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '16:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '17:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '18:00:00', 5, 0, NOW(), NOW(), false),
(1, '2026-01-01', '19:00:00', 5, 0, NOW(), NOW(), false);

-- store_id 2번
INSERT INTO store_remain (store_id, remain_date, remain_time, remain_team, version, created_at, updated_at, is_deleted) VALUES
(2, '2026-01-01', '10:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '11:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '12:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '13:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '14:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '15:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '16:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '17:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '18:00:00', 5, 0, NOW(), NOW(), false),
(2, '2026-01-01', '19:00:00', 5, 0, NOW(), NOW(), false);

-- store_id 3번
INSERT INTO store_remain (store_id, remain_date, remain_time, remain_team, version, created_at, updated_at, is_deleted) VALUES
(3, '2026-01-01', '10:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '11:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '12:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '13:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '14:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '15:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '16:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '17:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '18:00:00', 5, 0, NOW(), NOW(), false),
(3, '2026-01-01', '19:00:00', 5, 0, NOW(), NOW(), false);

-- store_id 4번
INSERT INTO store_remain (store_id, remain_date, remain_time, remain_team, version, created_at, updated_at, is_deleted) VALUES
(4, '2026-01-01', '10:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '11:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '12:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '13:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '14:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '15:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '16:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '17:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '18:00:00', 5, 0, NOW(), NOW(), false),
(4, '2026-01-01', '19:00:00', 5, 0, NOW(), NOW(), false);

-- store_id 5번
INSERT INTO store_remain (store_id, remain_date, remain_time, remain_team, version, created_at, updated_at, is_deleted) VALUES
(5, '2026-01-01', '10:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '11:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '12:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '13:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '14:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '15:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '16:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '17:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '18:00:00', 5, 0, NOW(), NOW(), false),
(5, '2026-01-01', '19:00:00', 5, 0, NOW(), NOW(), false);
