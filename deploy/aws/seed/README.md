# AWS 시연 시드

명시적으로 SEED_RUN_ID 를 넣을 때만 실행한다. 환경 시작 시 자동 실행하지 않고, 기존 테이블을 비우지 않는다. 같은 UUID는 다시 넣어도 추가되지 않는다.

시드 계정의 BCrypt 비밀번호 해시를 `SEED_PASSWORD_HASH`로 반드시 전달한다. AWS에서는 Secrets Manager의 값을 일회성 태스크에 주입하며, 비밀번호를 문서나 실행 로그에 남기지 않는다. 값이 없으면 DB에 접근하기 전에 실패한다. `fixtures.env`의 고정 해시와 `FixturePassw0rd!`는 로컬 테스트 전용이며 AWS 기본값으로 사용하지 않는다.

| 역할 | 이메일 | 비고 |
| --- | --- | --- |
| MASTER | master@aws-fixture.example | 쿠폰 created_by |
| MANAGER | manager@aws-fixture.example | 마스터가 생성 |
| CREATOR | creator@aws-fixture.example | 승인 완료. 상품 creator_id 는 이 사용자 UUID |
| CUSTOMER | customer@aws-fixture.example | 소비자 |

상품은 기본 SKU 하나와 재고 50, 쿠폰은 10% 시연 쿠폰이다. Slack 행과 외부 결제 행은 넣지 않는다. 쿠폰 Redis 잔여 키는 시드하지 않는다.

호출 순서는 user, product, order 이다. 상세 입력은 database/INTERFACE.md 를 본다.
