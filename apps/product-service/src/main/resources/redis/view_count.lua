local viewerKey = KEYS[1] -- 방문자 ID
local viewCountKey = KEYS[2] -- 조회수 집계 키

local ttl = ARGV[1] -- 중복 방지 TTL
local productId = ARGV[2] -- 상품 PK

local result = redis.call(
        'SET',
        viewerKey,
        '1',
        'NX',
        'EX',
        ttl
)

--
if not result then
    return 0
end

redis.call(

        viewCountKey,
        productId,
        1
)

return 1