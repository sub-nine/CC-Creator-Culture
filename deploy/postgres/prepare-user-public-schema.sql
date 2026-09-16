-- User 서비스 전체 중단 및 백업 후, 해당 DB의 테이블 소유자로 실행한다.
-- psql --set=ON_ERROR_STOP=1 --file=deploy/postgres/prepare-user-public-schema.sql
BEGIN;
SET LOCAL lock_timeout = '10s';
DO $$
DECLARE
    table_name text;
BEGIN
    IF to_regclass('private.flyway_schema_history') IS NULL
       OR to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        RAISE EXCEPTION 'Expected history only in private; inspect database before proceeding';
    END IF;
    IF (SELECT count(*) FROM private.flyway_schema_history WHERE version = '1' AND success AND type = 'SQL') <> 1
       OR EXISTS (SELECT 1 FROM private.flyway_schema_history
                  WHERE NOT success OR (version IS NOT NULL AND version <> '1')) THEN
        RAISE EXCEPTION 'Expected successful V1 history only';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'private' AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
          AND c.relname NOT IN ('flyway_schema_history', 'p_users', 'p_creators',
              'notification_event', 'notification', 'p_notification_slack', 'p_follows')
    ) THEN
        RAISE EXCEPTION 'Unexpected objects in private schema';
    END IF;
    FOREACH table_name IN ARRAY ARRAY['p_users', 'p_creators', 'notification_event', 'notification', 'p_notification_slack'] LOOP
        IF to_regclass(format('private.%I', table_name)) IS NULL THEN
            RAISE EXCEPTION 'Missing source table private.%', table_name;
        END IF;
    END LOOP;
    FOREACH table_name IN ARRAY ARRAY['p_users', 'p_creators', 'notification_event', 'notification', 'p_notification_slack', 'p_follows'] LOOP
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
            RAISE EXCEPTION 'Target public.% already exists', table_name;
        END IF;
    END LOOP;
    ALTER TABLE private.flyway_schema_history SET SCHEMA public;
END $$;
COMMIT;
