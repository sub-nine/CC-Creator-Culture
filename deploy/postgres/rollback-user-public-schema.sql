-- 서비스 전체 중단 상태에서만 실행한다. V2/V3 이외의 후속 마이그레이션이 있으면 중단한다.
-- psql --set=ON_ERROR_STOP=1 --file=deploy/postgres/rollback-user-public-schema.sql
BEGIN;
SET LOCAL lock_timeout = '10s';
CREATE SCHEMA IF NOT EXISTS private;
DO $$
DECLARE
    table_name text;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL
       OR to_regclass('private.flyway_schema_history') IS NOT NULL THEN
        RAISE EXCEPTION 'Expected history only in public';
    END IF;
    IF (SELECT count(*) FROM public.flyway_schema_history WHERE version = '1' AND success AND type = 'SQL') <> 1
       OR EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE NOT success
           OR (version IS NOT NULL AND version NOT IN ('1', '2', '3'))
           OR (version = '2' AND script <> 'V2__move_user_tables_to_public.sql')
           OR (version = '3' AND script <> 'V3__create_missing_follows.sql')) THEN
        RAISE EXCEPTION 'Unexpected migration history; restore from reviewed backup instead';
    END IF;
    FOREACH table_name IN ARRAY ARRAY['p_users', 'p_creators', 'notification_event', 'notification', 'p_notification_slack', 'p_follows'] LOOP
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL
           AND to_regclass(format('private.%I', table_name)) IS NOT NULL THEN
            RAISE EXCEPTION 'Both schemas contain %', table_name;
        END IF;
        IF table_name <> 'p_follows' AND to_regclass(format('public.%I', table_name)) IS NULL
           AND to_regclass(format('private.%I', table_name)) IS NULL THEN
            RAISE EXCEPTION 'Missing table %', table_name;
        END IF;
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA private', table_name);
        END IF;
    END LOOP;
    -- 애플리케이션 데이터는 삭제하지 않는다. 이 전환의 성공 이력만 되돌린다.
    DELETE FROM public.flyway_schema_history WHERE version IN ('2', '3');
    ALTER TABLE public.flyway_schema_history SET SCHEMA private;
END $$;
COMMIT;
