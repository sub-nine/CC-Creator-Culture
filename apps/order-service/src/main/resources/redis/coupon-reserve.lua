-- KEYS[1] = coupon:{couponId}:remaining
-- KEYS[2] = coupon:{couponId}:issued:{userId}
-- ARGV[1] = 선점 키 TTL(초)
-- ARGV[2] = reservationId

if redis.call('EXISTS', KEYS[2]) == 1 then
    return -1
end

local remaining = redis.call('GET', KEYS[1])
if remaining == false then
    return -3
end

if tonumber(remaining) <= 0 then
    return -2
end

redis.call('DECR', KEYS[1])
redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[1])

return 1
