-- 선착순 쿠폰 발급 원자 처리
-- KEYS[1] = coupon:stock:{templateId}    (재고 INTEGER)
-- KEYS[2] = coupon:issued:{templateId}   (발급 완료 userId SET)
-- ARGV[1] = userId
--
-- 반환:
--   1  = 발급 성공
--   0  = 중복 발급 (이미 받은 사용자)
--  -1  = 재고 소진
--  -2  = 템플릿 미존재 / 미오픈 / 만료 (stock 키 없음)

if redis.call('EXISTS', KEYS[1]) == 0 then
    return -2
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 0
end

local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
    return -1
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 1
