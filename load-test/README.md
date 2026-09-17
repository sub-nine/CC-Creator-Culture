# k6 부하 테스트

product-service, order-service, user-service 등 이 프로젝트의 API 엔드포인트에 대한 k6 부하 테스트 스크립트 모음입니다. Docker만 있으면 실행할 수 있으며, k6를 로컬에 별도로 설치할 필요는 없습니다.

## 사전 조건

### local

레포 루트에서 로컬 앱 스택을 띄우고, creator 승인처럼 admin 권한이 필요한 흐름을 위한 MASTER 계정을 시드합니다.

```bash
sh load-test/scripts/up.sh
```

이 계정은 Flyway 마이그레이션이 아니라 로컬 DB에 직접 심는 방식입니다. local과 dev가 같은 `dev` 스프링 프로파일을 공유하기 때문에, 마이그레이션에 넣으면 공유 dev 서버에도 그대로 적용되어 버립니다. 스택을 새로 띄우지 않고 계정만 다시 심고 싶다면 `sh load-test/scripts/seed-master.sh`만 실행해도 됩니다.

`up.sh`가 한 번에 성공하지 않는 경우가 종종 있습니다 (서비스 기동 순서/타이밍 문제로 추정). 그럴 땐 `up.sh`를 한 번 더 실행하면 됩니다.

### dev / prod

이미 배포되어 있는 서버를 대상으로 하므로 로컬에서 별도로 띄울 것은 없습니다. 다만 `config/environments/dev.js`, `prod.js`에 대상 서버의 baseUrl을 설정해야 합니다 (현재 TODO 상태).

## 실행

### 스크립트 하나만 실행

```bash
sh load-test/scripts/run.sh scenarios/product-service/search-products.js
```

### scenarios/ 전체 실행

```bash
sh load-test/scripts/run-all.sh
```

### 대상 환경 전환

기본값은 `local`이며, 두 스크립트 모두 마지막 인자로 대상 환경을 받습니다.

```bash
sh load-test/scripts/run.sh scenarios/product-service/search-products.js dev
sh load-test/scripts/run-all.sh dev
```

## 결과 확인

콘솔 요약 외에, 실행 중인 지표를 Grafana 대시보드로도 볼 수 있습니다. 실행 결과는 Prometheus로 remote write 되고, `sh load-test/scripts/up.sh`로 띄운 스택에 k6 전용 대시보드가 미리 provisioning 되어 있습니다.

- Grafana: http://localhost:3000 (k6 폴더의 "k6 Prometheus" 대시보드)
- 같은 실행에서 나온 시나리오들은 `testid` 변수로 묶여서 필터링됩니다.

## 테스트 데이터 정리

시나리오가 만드는 데이터는 `k6-test-`로 시작하는 프리픽스를 Unique 값으로 씁니다. local은 DB가 로컬 볼륨이라 정리 대신 통째로 밀고 다시 띄우는 게 더 간단합니다:

```bash
sh load-test/scripts/teardown.sh
```

볼륨을 밀고 재기동한 뒤 MASTER 계정 시드까지 이 스크립트 하나로 끝납니다.

dev처럼 공유 DB를 밀 수 없는 환경에서는 이 방식을 쓸 수 없으니 별도 정리 방법이 필요합니다 (TODO).

## 클라우드 실행

OCI는 배포된 이미지로 `scripts/run-dev.sh`, AWS는 일회성 Fargate 태스크로 `scripts/run-aws.sh`를 사용합니다. 두 실행기 모두 시나리오를 지정해야 하며 배포만으로 실행되지 않습니다.

연결 확인은 `scenarios/smoke.js`(읽기 1회)와 `scenarios/embedding-smoke.js`(추론 1회)를 사용합니다. 전체 실행기는 이 두 파일을 제외합니다. 주소, 환경 파일, 중지 방법과 지표 확인은 [클라우드 운영 절차](../deploy/cloud-load-test.md)를 참고하세요.
