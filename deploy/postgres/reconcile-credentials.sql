\set ON_ERROR_STOP on
\getenv postgres_user POSTGRES_USER
\getenv postgres_admin_password POSTGRES_PASSWORD
\getenv service_db_name SERVICE_DB_NAME
\getenv service_db_username SERVICE_DB_USERNAME
\getenv service_db_password SERVICE_DB_PASSWORD
SET log_min_error_statement = PANIC;
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'postgres_user', :'postgres_admin_password') \gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'service_db_username', :'service_db_password')
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_roles
  WHERE rolname = :'service_db_username'
)
\gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'service_db_username', :'service_db_password') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'service_db_name', :'service_db_username') \gexec
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'service_db_name') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'service_db_name', :'service_db_username') \gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'service_db_username') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'service_db_username') \gexec
