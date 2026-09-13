# AWS DB 마이그레이션/시드 인터페이스

워크플로 에이전트가 일회성 태스크로 호출하는 입력과 산출물이다. RDS 엔드포인트나 Secrets Manager ARN은 여기서 만들지 않는다. 호출 측이 실제 값을 넣는다.

## 산출 이미지

마이그레이션 이미지 빌드 컨텍스트는 deploy/aws 이다. SQL은 이미지에 복사되며 실행 시점에 git clone 하지 않는다. 태그와 RELEASE_SHA 빌드 인자로 릴리스 커밋을 고정한다.

- 마이그레이션 Dockerfile: deploy/aws/database/Dockerfile
- 시드/역할 Dockerfile: deploy/aws/seed/Dockerfile
- 권장 태그: cc-db-migrate:<RELEASE_SHA>, cc-db-seed:<RELEASE_SHA>
- 앱 컨테이너 시작, sidecar, 환경 부팅 훅에 연결하지 않는다. ECS RunTask 일회성 작업만 사용한다.

## 서비스와 DB

서비스별 전용 DB다. 한 작업은 SERVICE 하나다.

| SERVICE | 기본 스키마 | 마이그레이션 위치 |
| --- | --- | --- |
| user | private | /flyway/sql/user |
| product | public | /flyway/sql/product |
| order | public | /flyway/sql/order |

실제 코드에 vector 컬럼이 없어 CREATE EXTENSION vector 는 넣지 않았다. similarity_score 는 double 이다. Hibernate ddl-auto=validate 는 마이그레이션 이후 앱 설정(다른 담당)이다.

## TLS

이미지를 빌드할 때 AWS 공식 주소에서 RDS 서울(ap-northeast-2) CA 번들을 내려받고, Docker의 ADD --checksum으로 SHA-256을 검증한 뒤 /certs/rds-global-bundle.pem에 넣는다. 인증서 원본은 Git에 저장하지 않는다. 다운로드 또는 체크섬 검증에 실패하면 빌드도 실패한다. 인증서를 갱신할 때는 출처와 내용을 확인한 뒤 앱, 마이그레이션, 시드 Dockerfile의 체크섬을 함께 갱신한다. 파일명은 기존 경로 호환용이며 내용은 공식 리전 번들이다. AWS에서는 verify-full 을 쓴다. 로컬 컨테이너 검사는 sslmode=disable 을 허용한다.

마이그레이션은 FLYWAY_SSLMODE=verify-full 이면 URL에 sslmode 가 없을 때 sslmode=verify-full 과 sslrootcert=/certs/rds-global-bundle.pem 을 붙인다. URL에 이미 sslmode 가 있으면 그대로 둔다.

부트스트랩과 시드는 PGSSLMODE=verify-full 을 쓰고, PGSSLROOTCERT 기본값은 /certs/rds-global-bundle.pem 이다.

## 권한

부트스트랩은 앱 역할에 CONNECT, CREATE, TEMPORARY 를 준다. Flyway가 스키마와 테이블을 만들기 위해서다. 현재 앱 설정도 같은 DB 사용자를 쓰므로 첫 배포에서는 마이그레이션과 런타임이 이 역할을 공유한다. 일상 런타임을 DML 전용 역할로 나누는 작업은 아직 없다. 런타임 사용자를 따로 둘 때 마이그레이션 역할만 CREATE 를 유지하면 된다.

## 부트스트랩 (선택, 관리자 자격)

DB 인스턴스와 데이터베이스는 Terraform 등이 이미 만든 상태를 전제로 한다. 이 스크립트는 앱 역할과 스키마 권한만 맞춘다. CREATE DATABASE 와 postgres 슈퍼유저 전제(ALTER DATABASE OWNER, ALTER SCHEMA public OWNER)는 넣지 않는다.

시드 이미지 엔트리포인트를 바꿔 실행한다.

필수 환경 변수: SERVICE, DB_HOST, DB_NAME, ADMIN_USER, APP_USER
비밀번호: ADMIN_PASSWORD 또는 ADMIN_PASSWORD_FILE, APP_PASSWORD 또는 APP_PASSWORD_FILE
선택: DB_PORT(기본 5432), PGSSLMODE, PGSSLROOTCERT

예시:

    docker run --rm --entrypoint /scripts/bootstrap-roles.sh
      -e SERVICE=user
      -e DB_HOST=<rds-endpoint>
      -e DB_PORT=5432
      -e DB_NAME=<user_db>
      -e ADMIN_USER=<master>
      -e ADMIN_PASSWORD_FILE=/run/secrets/admin
      -e APP_USER=<user_app>
      -e APP_PASSWORD_FILE=/run/secrets/app
      -e PGSSLMODE=verify-full
      cc-db-seed:<RELEASE_SHA>

user 서비스는 private 스키마를 만들고 앱 역할의 search_path 를 private, public 로 둔다.

## 마이그레이션 (필수, 앱 역할)

엔트리포인트가 migrate 다. Flyway Community CLI만 사용한다. 서비스당 한 번 적용하고 같은 버전을 다시 실행하면 no-op 이다.

필수: SERVICE, FLYWAY_URL, FLYWAY_USER
비밀번호: FLYWAY_PASSWORD 또는 FLYWAY_PASSWORD_FILE
선택: FLYWAY_CONNECT_RETRIES(기본 10), RELEASE_SHA(로그 표기), FLYWAY_SSLMODE, FLYWAY_SSLROOTCERT

FLYWAY_URL 이 없으면 DB_HOST, DB_PORT, DB_NAME 으로 JDBC URL을 만든다. FLYWAY_USER/PASSWORD 대신 DB_USER, DB_PASSWORD, DB_PASSWORD_FILE 도 받는다. 시드와 같은 이름이다.

ECS RunTask가 SERVICE 만 바꾸면 세 RDS에 같은 URL이 나간다. 서비스마다 DB_HOST, DB_NAME, DB_USER, 비밀번호를 override 하거나, migrate_task_families 로 태스크 정의를 세 개 둔다.

JDBC URL 예:

    jdbc:postgresql://<rds-endpoint>:5432/<db_name>

실행 예:

    docker run --rm
      -e SERVICE=user
      -e FLYWAY_URL=jdbc:postgresql://<rds-endpoint>:5432/<user_db>
      -e FLYWAY_SSLMODE=verify-full
      -e FLYWAY_USER=<user_app>
      -e FLYWAY_PASSWORD_FILE=/run/secrets/app
      -e RELEASE_SHA=<git-sha>
      cc-db-migrate:<RELEASE_SHA>

실행 순서: user, product, order 각각 독립. 스키마 의존은 없다.

## 시드 (명시 호출만)

자동 실행하지 않는다. SEED_RUN_ID 없이 실패한다. TRUNCATE/DROP 하지 않고 고정 UUID에 ON CONFLICT DO NOTHING 이다. 같은 런 ID를 다시 넣어도 행이 늘지 않는다.

필수: SERVICE, SEED_RUN_ID, DB_HOST, DB_NAME, DB_USER, SEED_PASSWORD_HASH (BCrypt)
비밀번호: DB_PASSWORD 또는 DB_PASSWORD_FILE
선택: DB_PORT, PGSSLMODE

호출 순서: user, 이어서 product, 이어서 order. 상품 creator_id 는 창작자 사용자 UUID(p_users.id)이고 p_creators.id 가 아니다. 쿠폰 created_by 는 마스터 사용자 UUID다.

    docker run --rm
      -e SERVICE=user
      -e SEED_RUN_ID=demo-20260913
      -e SEED_PASSWORD_HASH
      -e DB_HOST=<rds-endpoint>
      -e DB_NAME=<user_db>
      -e DB_USER=<user_app>
      -e DB_PASSWORD_FILE=/run/secrets/app
      -e PGSSLMODE=verify-full
      cc-db-seed:<RELEASE_SHA>

고정 계정은 deploy/aws/seed/fixtures.env 와 README를 본다. Slack/외부 결제 행은 넣지 않는다. 쿠폰 Redis 잔여 키는 시드하지 않으며, 발급 경로의 SET NX 지연 초기화에 맡긴다.

## ECS 일회성 작업

launchType 과 capacityProviderStrategy 는 워크플로 담당이 클러스터에 맞게 넣는다.
네트워크는 RDS 보안 그룹이 허용하는 사설 서브넷의 awsvpc 이다.
시크릿은 태스크 정의의 Secrets Manager 주입을 쓰고 이미지에 비밀번호를 넣지 않는다.
앱 서비스 desired count 와 무관하게 RunTask 로만 실행한다.
마이그레이션 성공 후 앱을 띄우고 Hibernate validate 를 확인한다.
시드는 데모/시험 때만 SEED_RUN_ID 를 넣어 명시 호출한다. 환경 시작 시 자동 호출하지 않는다.
