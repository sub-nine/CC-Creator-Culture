-- V1 체크섬은 유지한다. 기존 DB의 Flyway 이력은 기동 전에 public으로 이전해야 한다.
SET LOCAL lock_timeout = '10s';

DO $$
DECLARE
    table_name text;
    tables constant text[] := ARRAY[
        'p_users', 'p_creators', 'notification_event', 'notification', 'p_notification_slack'
    ];
BEGIN
    IF to_regclass('private.flyway_schema_history') IS NOT NULL THEN
        RAISE EXCEPTION 'Move private.flyway_schema_history to public before starting user-service';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'private' AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
          AND c.relname <> ALL(tables || ARRAY['p_follows'])
    ) THEN
        RAISE EXCEPTION 'Unexpected objects in private schema; inspect before migration';
    END IF;
    FOREACH table_name IN ARRAY tables LOOP
        IF to_regclass(format('private.%I', table_name)) IS NULL THEN
            RAISE EXCEPTION 'Missing source table private.%', table_name;
        END IF;
    END LOOP;
    FOREACH table_name IN ARRAY tables || ARRAY['p_follows'] LOOP
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
            RAISE EXCEPTION 'Target public.% already exists', table_name;
        END IF;
    END LOOP;
    FOREACH table_name IN ARRAY tables || ARRAY['p_follows'] LOOP
        IF to_regclass(format('private.%I', table_name)) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE private.%I SET SCHEMA public', table_name);
        END IF;
    END LOOP;
END $$;
