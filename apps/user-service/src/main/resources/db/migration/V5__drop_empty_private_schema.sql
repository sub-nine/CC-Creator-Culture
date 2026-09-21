-- 적용된 V2/V4는 보존하고, 남은 빈 스키마 정리만 새 버전으로 수행한다.
-- 예상하지 못한 객체가 있으면 삭제하지 않고 마이그레이션을 중단한다.
SET LOCAL lock_timeout = '10s';
DROP SCHEMA IF EXISTS private RESTRICT;
