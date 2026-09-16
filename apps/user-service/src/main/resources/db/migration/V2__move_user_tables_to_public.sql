ALTER TABLE IF EXISTS private.p_users SET SCHEMA public;
ALTER TABLE IF EXISTS private.p_creators SET SCHEMA public;
ALTER TABLE IF EXISTS private.p_follows SET SCHEMA public;
ALTER TABLE IF EXISTS private.notification_event SET SCHEMA public;
ALTER TABLE IF EXISTS private.notification SET SCHEMA public;
ALTER TABLE IF EXISTS private.p_notification_slack SET SCHEMA public;
DROP SCHEMA IF EXISTS private;
