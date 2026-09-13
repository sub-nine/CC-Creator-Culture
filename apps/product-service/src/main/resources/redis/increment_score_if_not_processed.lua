-- KEYS[1] = 중복 처리 방지 마커 키
-- KEYS[2] = 카테고리 리더보드 ZSET 키
-- KEYS[3] = 해시태그 리더보드 ZSET 키
-- ARGV[1] = 마커 TTL(초)
-- ARGV[2..] = (keyIndex, member, delta) 3개씩 반복 - keyIndex는 2(카테고리) 또는 3(해시태그)

-- TODO: 카테고리/해시태그 점수가 둘 다 비어있어도(예: 주문 시점에 아직 해시태그-상품 연결이
--  안 끝난 경우) 아래 SET NX가 무조건 성공해 처리 완료로 마킹됨 - 실제로는 점수가 하나도
--  반영 안 됐는데 이 keyId(주문 등)는 영구적으로 재처리가 안 되어 점수가 유실됨.
--  점수 항목이 하나도 없을 땐 마커를 찍지 않거나, 별도 재처리 경로가 필요함.
local ttl = tonumber(ARGV[1])

local claimed = redis.call('SET', KEYS[1], '1', 'NX', 'EX', ttl)
if not claimed then
    return 0
end

for i = 2, #ARGV, 3 do
    local zsetKey = KEYS[tonumber(ARGV[i])]
    local member = ARGV[i + 1]
    local delta = ARGV[i + 2]
    redis.call('ZINCRBY', zsetKey, delta, member)
end

return 1
