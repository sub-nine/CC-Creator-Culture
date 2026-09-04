-- Refresh Token 삭제와 Access Token 블랙리스트 등록을 원자적으로 처리

redis.call('DEL', KEYS[1])

local ttl = tonumber(ARGV[2])
if ttl > 0 then
    redis.call('SET', KEYS[2], ARGV[1], 'EX', ttl)
end

return 1
