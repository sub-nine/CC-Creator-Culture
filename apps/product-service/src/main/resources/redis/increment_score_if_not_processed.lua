-- KEYS[1] = 중복 처리 방지 마커 키
-- KEYS[2] = 카테고리 리더보드 ZSET 키
-- KEYS[3] = 해시태그 리더보드 ZSET 키
-- ARGV[1] = 마커 TTL(초)
-- ARGV[2..] = (keyIndex, member, delta) 3개씩 반복 - keyIndex는 2(카테고리) 또는 3(해시태그)

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
