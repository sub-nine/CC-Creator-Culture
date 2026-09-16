-- KEYS[1] = 스냅샷 대상 리더보드 ZSET 키(leaderboard:current:*)

-- 조회와 삭제를 하나의 스크립트로 묶어 원자적으로 실행 - 그 사이 다른 클라이언트의 ZINCRBY가
-- 끼어들어 유실되는 걸 방지(둘을 별도 명령으로 호출하면 그 사이에 들어온 증가분이 삭제로 함께 날아감)
local members = redis.call('ZREVRANGE', KEYS[1], 0, -1, 'WITHSCORES')
redis.call('DEL', KEYS[1])
return members
