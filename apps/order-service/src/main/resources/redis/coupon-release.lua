-- reservationId가 일치하는 선점만 해제하고 잔여 수량을 한 번만 복구한다.
-- KEYS[1] = coupon:{couponId}:remaining
-- KEYS[2] = coupon:{couponId}:issued:{userId}
-- ARGV[1] = reservationId

local owner = redis.call('GET', KEYS[2])
if owner == false then
    return 0
end

if owner ~= ARGV[1] then
    return -1
end

redis.call('DEL', KEYS[2])

if redis.call('EXISTS', KEYS[1]) == 0 then
    return 2
end

redis.call('INCR', KEYS[1])
return 1
