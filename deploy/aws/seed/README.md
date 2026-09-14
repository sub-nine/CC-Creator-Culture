# AWS 시연 시드

`aws-deploy.yml`의 `deploy` 액션에 `seed_run_id`를 넣을 때만 실행한다. 환경 시작 시 자동 실행하지 않고, 기존 테이블을 비우지 않는다. 같은 UUID는 다시 넣어도 추가되지 않으며, 실행 기록은 `ops.seed_runs(run_id, service)`에 남는다.

스키마는 각 서비스가 기동 시 Flyway로 만든다. 이 이미지는 데이터만 넣는다.

## 이미지

`deploy-dev.yml`의 `image` job이 이 디렉터리를 빌드 컨텍스트로 삼아 ECR `cc-test/db-seed:<sha>`에만 push 한다. OCI는 이 이미지를 쓰지 않으므로 OCIR에는 올리지 않으며, `AWS_ECR_REGISTRY` 변수가 비어 있으면 빌드 자체를 건너뛴다. Dockerfile은 이 디렉터리 밖의 파일을 참조하지 않는다.

## 입력 (환경 변수)

| 변수 | 설명 |
| --- | --- |
| `SERVICE` | `user`, `product`, `order` 중 하나 |
| `SEED_RUN_ID` | 실행 식별자. 필수 |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | 대상 DB. `DB_PORT` 기본값 5432 |
| `DB_PASSWORD` 또는 `DB_PASSWORD_FILE` | DB 비밀번호 |
| `SEED_PASSWORD_HASH` | 시드 계정의 BCrypt 해시. 기본값 없음 |
| `PGSSLMODE` | AWS에서는 `verify-full`. RDS CA 번들은 이미지에 포함 |

AWS에서는 Secrets Manager 값을 ECS 태스크 정의의 `secrets`로 주입하며, 비밀번호를 문서나 실행 로그에 남기지 않는다. `fixtures.env`의 고정 해시와 `FixturePassw0rd!`는 로컬 테스트 전용이다.

## 시드 데이터

| 역할 | 이메일 | 비고 |
| --- | --- | --- |
| MASTER | master@aws-fixture.example | 쿠폰 created_by |
| MANAGER | manager@aws-fixture.example | 마스터가 생성 |
| CREATOR | creator@aws-fixture.example | 승인 완료. 상품 creator_id 는 이 사용자 UUID |
| CUSTOMER | customer@aws-fixture.example | 소비자 |

상품은 기본 SKU 하나와 재고 50, 쿠폰은 10% 시연 쿠폰이다. Slack 행과 외부 결제 행은 넣지 않는다. 호출 순서는 user, product, order 이다.

## 로컬 검증

```bash
REQUIRE_DOCKER=true bash deploy/aws/seed/seed_test.sh
```

이미지를 빌드하고 PostgreSQL 17 컨테이너에 최소 스키마를 만든 뒤 시드를 1회 적용한다.
