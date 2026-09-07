local viewerKey = KEYS[1] -- 방문자별 조회 여부 확인 키
local viewCountKey = KEYS[2] -- 조회수 집계 키

local ttl = tonumber(ARGV[1])
local productId = ARGV[2] -- 상품 PK

local result = redis.call(
        'SET',
        viewerKey,
        '1',
        'NX',
        'EX',
        ttl
)

if not result then
    return 0
end

redis.call(
        'HINCRBY',
        viewCountKey,
        productId,
        1
)

return 1