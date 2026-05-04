-- 모든 매장에 카테고리별 메뉴 5~7개 랜덤 추가
-- 매장마다 메뉴 조합·개수가 다르게 들어감 (매장 ID 기반 해시 정렬)
-- 이미 메뉴가 있는 매장은 스킵 (NOT EXISTS 조건으로 멱등 실행 보장)
-- 실행 예시:
--   docker exec -i catchtable-db psql -U teamE -d catchtable < backend/data/all-menu-seed.sql

-- KOREAN (한식)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('김치찌개', '얼큰하고 진한 김치찌개', 9000),
        ('된장찌개', '구수한 된장찌개 한 그릇', 8000),
        ('비빔밥', '신선한 나물과 고추장 비빔밥', 11000),
        ('불고기 정식', '달콤 짭짤한 불고기 한상', 15000),
        ('갈비탕', '고기가 푸짐한 갈비탕', 13000),
        ('잡채', '쫄깃한 당면과 야채 잡채', 12000),
        ('삼계탕', '보양식 삼계탕', 16000),
        ('제육볶음', '매콤한 제육볶음', 11000),
        ('순두부찌개', '담백한 순두부찌개', 9000),
        ('떡볶이', '매콤달콤 떡볶이', 8000),
        ('김치볶음밥', '치즈 김치볶음밥', 10000),
        ('칼국수', '바지락 칼국수', 10000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'KOREAN' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- JAPANESE (일식)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('초밥 세트', '셰프 추천 초밥 10피스', 25000),
        ('연어 사시미', '신선한 연어 사시미', 22000),
        ('돈카츠', '바삭한 등심 돈카츠', 14000),
        ('우동', '진한 가츠오 육수 우동', 9000),
        ('규동', '얇게 썬 소고기 덮밥', 12000),
        ('가라아게', '바삭한 일본식 닭튀김', 10000),
        ('돈코츠 라멘', '진한 돼지뼈 육수 라멘', 12000),
        ('텐동', '튀김 덮밥', 13000),
        ('일본식 카레', '담백한 일본 카레', 11000),
        ('야키소바', '철판 볶음면', 10000),
        ('오니기리 세트', '주먹밥 3종 세트', 7000),
        ('스시 오마카세', '셰프 추천 코스', 80000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'JAPANESE' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- CHINESE (중식)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('짜장면', '수제 춘장 짜장면', 8000),
        ('짬뽕', '얼큰한 해물 짬뽕', 9000),
        ('탕수육', '바삭한 찹쌀 탕수육', 22000),
        ('볶음밥', '계란 볶음밥', 9000),
        ('마파두부', '얼얼한 사천식 마파두부', 14000),
        ('깐풍기', '매콤달콤 깐풍기', 23000),
        ('양장피', '냉채 양장피', 28000),
        ('군만두', '바삭한 군만두 8개', 7000),
        ('동파육', '본토식 동파육', 32000),
        ('깐쇼새우', '바삭 깐쇼새우', 25000),
        ('짬뽕밥', '얼큰 짬뽕밥', 10000),
        ('유산슬', '계절 유산슬', 24000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'CHINESE' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- WESTERN (양식)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('크림 파스타', '진한 크림 파스타', 16000),
        ('마르게리타 피자', '토마토 모짜렐라 바질', 19000),
        ('립아이 스테이크', '미디엄 굽기 추천', 38000),
        ('시저 샐러드', '신선한 로메인과 시저 드레싱', 13000),
        ('해산물 리조또', '새우와 관자가 들어간 리조또', 21000),
        ('포카치아', '갓 구운 포카치아 빵', 8000),
        ('봉골레 파스타', '바지락 화이트 와인 파스타', 17000),
        ('카르보나라', '베이컨 크림 카르보나라', 16000),
        ('함박 스테이크', '데미글라스 함박', 18000),
        ('그라탱', '치즈 그라탱', 15000),
        ('라자냐', '오븐에 구운 라자냐', 18000),
        ('티본 스테이크', '450g 티본 스테이크', 55000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'WESTERN' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- DESSERT (디저트/카페)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('아메리카노', '깊고 진한 에스프레소', 5000),
        ('카페 라떼', '부드러운 우유 라떼', 5500),
        ('티라미수', '마스카포네 치즈 티라미수', 7500),
        ('크림 브륄레', '캐러멜라이즈 크림 브륄레', 8000),
        ('마카롱 세트', '시즌 마카롱 4종', 9000),
        ('초콜릿 케이크', '진한 다크 초콜릿 케이크', 7000),
        ('카푸치노', '풍성한 거품의 카푸치노', 5800),
        ('바닐라 라떼', '바닐라 시럽 라떼', 6000),
        ('치즈케이크', '뉴욕 치즈케이크', 7500),
        ('크로플', '바삭한 크로플 위 아이스크림', 8500),
        ('수제 와플', '딸기 수제 와플', 9000),
        ('아인슈페너', '진한 크림 커피', 6500)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'DESSERT' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- CHICKEN (치킨)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('후라이드 치킨', '바삭한 후라이드 한 마리', 19000),
        ('양념 치킨', '매콤달콤 양념 치킨', 20000),
        ('간장 치킨', '달콤 짭짤 간장 치킨', 20000),
        ('파닭', '쪽파 듬뿍 파닭', 22000),
        ('핫윙', '매운 핫윙 8조각', 12000),
        ('치즈볼', '쫀득 치즈볼 8개', 5000),
        ('감자튀김', '바삭한 감자튀김', 4000),
        ('생맥주 500cc', '시원한 생맥주', 5000),
        ('콜라 1.25L', '콜라 1.25리터', 3000),
        ('뼈없는 순살', '순살 후라이드', 21000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'CHICKEN' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- MEAT (고기/구이)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('한우 등심 200g', '1++ 등급 한우 등심', 65000),
        ('한우 갈비살 200g', '한우 갈비살', 58000),
        ('삼겹살 200g', '국내산 통삼겹살', 18000),
        ('항정살 200g', '쫄깃한 항정살', 22000),
        ('차돌박이 150g', '얇게 썬 차돌박이', 25000),
        ('목살 200g', '국내산 통목살', 17000),
        ('우삼겹', '얇게 썬 우삼겹', 28000),
        ('된장찌개', '식사용 된장찌개', 8000),
        ('냉면', '시원한 평양냉면', 11000),
        ('공기밥', '쌀밥 한 공기', 2000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'MEAT' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- FISH (횟집)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('모듬회 (소)', '광어 우럭 등 모듬회', 55000),
        ('모듬회 (대)', '4인 기준 모듬회', 95000),
        ('참치회', '냉동 참치 사시미', 35000),
        ('광어회', '국내산 광어회', 45000),
        ('우럭회', '국내산 우럭회', 50000),
        ('연어회', '노르웨이산 연어', 38000),
        ('매운탕', '얼큰한 해물 매운탕', 25000),
        ('회덮밥', '모듬회 덮밥', 18000),
        ('초밥 세트', '초밥 12피스', 28000),
        ('새우튀김', '왕새우 튀김 5미', 15000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'FISH' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- FASTFOOD (패스트푸드)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('치즈버거 세트', '치즈버거 + 감자튀김 + 음료', 9500),
        ('더블버거 세트', '패티 2장 + 감자튀김 + 음료', 11500),
        ('베이컨 버거', '베이컨 듬뿍 버거', 8500),
        ('새우 버거', '통새우 패티 버거', 8000),
        ('치킨 너겟 6조각', '바삭한 치킨너겟', 4500),
        ('감자튀김 (L)', '라지 사이즈 감자튀김', 4000),
        ('아이스크림 콘', '바닐라 아이스크림 콘', 1500),
        ('콜라 (L)', '라지 사이즈 콜라', 2500),
        ('애플파이', '바삭한 애플파이', 2500),
        ('핫윙 4조각', '매운 핫윙', 5500)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'FASTFOOD' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- FOREIGN (외국음식)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('베트남 쌀국수', '진한 사골 쌀국수', 11000),
        ('분짜', '하노이식 분짜 정식', 13000),
        ('팟타이', '태국식 볶음 쌀국수', 12000),
        ('똠얌꿍', '매콤한 새우 똠얌꿍', 14000),
        ('치킨 카레', '인도식 치킨 카레와 난', 15000),
        ('그린 카레', '코코넛 그린 카레', 14000),
        ('짜조', '바삭한 베트남 스프링롤', 8000),
        ('쏨땀', '태국식 파파야 샐러드', 12000),
        ('파드 까파오', '태국 바질 볶음', 13000),
        ('마살라 도사', '인도식 크레페', 12000),
        ('케밥 플레이트', '터키식 케밥 정식', 16000),
        ('월남쌈', '신선한 월남쌈 모듬', 18000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'FOREIGN' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- BUFFET (뷔페)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('점심 뷔페 (성인)', '평일 점심 뷔페', 35000),
        ('저녁 뷔페 (성인)', '저녁 뷔페 풀코스', 55000),
        ('주말 브런치', '주말 한정 브런치 뷔페', 45000),
        ('어린이 뷔페', '7~12세 어린이 요금', 22000),
        ('유아 뷔페', '4~6세 유아 요금', 12000),
        ('음료 무제한', '소프트드링크 무제한', 8000),
        ('와인 페어링', '디너 와인 페어링', 25000),
        ('생일 케이크', '생일 축하 케이크', 30000),
        ('홀케이크 + 와인', '특별 패키지', 65000),
        ('VIP 룸 이용료', 'VIP 룸 추가', 50000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'BUFFET' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);

-- OTHERS (기타)
INSERT INTO menu (store_id, menu_name, description, price, is_deleted, created_at)
SELECT s.id, m.menu_name, m.description, m.price, false, NOW()
FROM stores s
CROSS JOIN LATERAL (
    SELECT menu_name, description, price
    FROM (VALUES
        ('대표 메뉴 A', '셰프 추천 대표 메뉴', 15000),
        ('대표 메뉴 B', '인기 메뉴', 13000),
        ('런치 세트', '평일 점심 한정 세트', 12000),
        ('디너 세트', '저녁 코스 메뉴', 25000),
        ('주말 한정 코스', '주말에만 제공', 32000),
        ('시즌 한정', '계절 한정 메뉴', 18000),
        ('사이드 메뉴', '추천 사이드', 6000),
        ('디저트', '오늘의 디저트', 7000),
        ('계절 음료', '시즌 한정 음료', 4500),
        ('주류 세트', '계절 주류 세트', 25000)
    ) AS pool(menu_name, description, price)
    ORDER BY md5(s.id::text || menu_name)
    LIMIT (5 + (s.id % 3))::int
) AS m
WHERE s.category = 'OTHERS' AND s.is_deleted = false
  AND NOT EXISTS (SELECT 1 FROM menu WHERE store_id = s.id AND is_deleted = false);
