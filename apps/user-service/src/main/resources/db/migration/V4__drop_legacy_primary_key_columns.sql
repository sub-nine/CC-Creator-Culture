-- 기본키를 id로 변경한 뒤 Hibernate update가 남긴 미사용 컬럼을 제거한다.
-- p_creators.user_id와 p_follows의 user_id/creator_id는 현재 사용하는 FK이므로 유지한다.
SET LOCAL lock_timeout = '10s';
ALTER TABLE public.p_users DROP COLUMN IF EXISTS user_id RESTRICT;
ALTER TABLE public.p_creators DROP COLUMN IF EXISTS creator_id RESTRICT;
