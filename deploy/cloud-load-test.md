# 임베딩 배포와 k6 실행 환경

이 문서는 #219의 배포 및 연결 확인 절차다. k6는 실행 환경만 준비하고, 운영자가 시나리오를 지정할 때만 실행한다. 배포 과정에서 전체 부하 시나리오를 자동 실행하지 않는다.

## 구성

| 항목 | OCI 개발 서버 | AWS |
| --- | --- | --- |
| 임베딩 | Compose, 0.5 CPU / 1,536MiB | ARM64 Fargate 서비스, 0.5 vCPU / 2GiB |
| k6 | 별도 Compose 수동 실행 | ARM64 Fargate 일회성 태스크, 1 vCPU / 2GiB |
| 결과 지표 | 개발 서버 Prometheus | 기존 관측 EC2 Prometheus |
| 결과 조회 | 기존 Grafana k6 대시보드 | SSM으로 접근하는 Grafana k6 대시보드 |

임베딩 모델과 `POST /embed` API는 기존과 같다. 이미지 빌드 때 `jhgan/ko-sroberta-multitask`를 받고, 실행 시에는 `HF_HUB_OFFLINE=1`로 이미지에 포함된 모델만 사용한다. `/docs` 헬스체크와 실제 추론 성공은 별도로 확인한다.

CPU와 메모리는 초기 설정값이다. 최대 처리량과 적정 사양은 아직 측정하지 않았다. k6를 별도 태스크로 실행해도 Prometheus의 수집 용량은 향후 부하테스트 때 함께 확인해야 한다.

## 배포 전 확인

- 현재 클라우드 배포 이미지와 설정 버전, dev 서버의 메모리 및 디스크 여유 확인
- OCIR 저장 용량 제한 확인. 임베딩 이미지에는 모델과 CPU 추론 라이브러리가 들어가므로 기존 Java 이미지보다 커질 수 있음
- Product DB의 `vector` 확장과 `V3__add_category_vector_tables.sql` 적용 상태 확인. 기존 마이그레이션 변경과 전체 벡터 재계산은 수행하지 않음
- Terraform plan에서 기존 자원의 삭제 및 교체 여부 확인. 실제 state를 읽은 plan과 로컬 `validate` 결과 구분
- OCI의 이전 릴리스 이미지와 배포 스냅샷 유지. 이전 릴리스에 임베딩 이미지가 없더라도 복구 가능 여부 확인

## 배포 순서

1. OCI와 AWS에 임베딩 및 k6 이미지 저장소와 필요한 게시 권한을 준비한다.
2. 개발 배포 워크플로로 이미지를 검증하고 게시한다. Python과 k6 이미지에는 Gradle 빌드 및 Java 설정 레이블을 적용하지 않는다.
3. OCI에서 임베딩 준비 후 Product를 시작하고, 관측 프로파일로 Prometheus와 Grafana를 준비한다.
4. AWS bootstrap/persistent 변경을 먼저 적용하고 변경된 persistent 출력을 runtime에 전달한다.
5. 게시된 이미지 태그로 AWS runtime을 배포한다. 임베딩 서비스가 준비된 뒤 Product를 시작하며 k6는 태스크 정의만 등록한다.
6. 아래 최소 연결 확인을 수동으로 수행한다.

AWS `start`/`stop`은 임베딩 서비스를 포함한다. k6는 ECS 서비스가 아니므로 원하는 태스크 수를 유지하거나 자동으로 재시작하지 않는다. 실행 중인 k6는 별도로 중지하고 나서 환경을 정지한다.

## 최소 연결 확인

임베딩 컨테이너에서 `python smoke.py`를 실행하면 `/docs` 준비를 기다린 뒤 `/embed`를 한 번 호출하고 벡터의 길이 768과 모든 값이 유한한 숫자인지 검사한다. 이 확인은 데이터베이스에 값을 쓰지 않는다.

Product 컨테이너 또는 태스크의 실행 네트워크에서 `EMBEDDING_SERVICE_URL`로 접근 가능한지도 별도로 확인한다. 임베딩 자체가 정상이어도 Product의 주소나 보안 그룹이 잘못되면 연동은 실패할 수 있다.

k6는 1 VU, 1회 요청만 보내는 별도 연결 확인 스크립트를 사용한다. 일반 부하 시나리오는 준비 단계에서 회원가입이나 테스트 데이터를 만들 수 있으므로 연결 확인에 사용하지 않는다.

완료 시 실행한 `TESTID`, k6 종료 코드, Prometheus의 해당 지표, Grafana의 해당 실행 결과를 함께 기록한다. AWS에서는 태스크가 `STOPPED` 상태이고 컨테이너 종료 코드가 0인지 확인한다. 로그와 요약은 CloudWatch에 남기며, 태스크 안에만 저장된 파일을 결과 보관 수단으로 사용하지 않는다.

## 수동 실행 명령

OCI 서버에서 현재 릴리스의 `source` 디렉터리로 이동한 뒤 실행한다. `ENV_FILE`은 배포 상태 디렉터리의 `runtime/current.env` 절대 경로다. 기본 네트워크는 `cc-dev_internal`이며 변경한 경우 `K6_NETWORK`를 지정한다.

```sh
ENV_FILE=/path/to/deploy-state/runtime/current.env \
SMOKE_URL=http://embedding-service:8000/docs \
TESTID=dev-read-219 sh load-test/scripts/run-dev.sh scenarios/smoke.js

ENV_FILE=/path/to/deploy-state/runtime/current.env \
EMBED_URL=http://embedding-service:8000/embed \
TESTID=dev-embed-219 sh load-test/scripts/run-dev.sh scenarios/embedding-smoke.js
```

AWS는 실제 backend가 초기화된 runtime 디렉터리와 유효한 AWS CLI 세션이 필요하다. 다른 디렉터리를 사용하면 `TERRAFORM_DIR`을 지정한다.

```sh
AWS_PROFILE=cc-prod TESTID=aws-read-219 \
sh load-test/scripts/run-aws.sh run scenarios/smoke.js

AWS_PROFILE=cc-prod TESTID=aws-embed-219 \
sh load-test/scripts/run-aws.sh run scenarios/embedding-smoke.js

AWS_PROFILE=cc-prod sh load-test/scripts/run-aws.sh stop '<출력된 TASK_ARN>'
```

AWS 실행기는 태스크 종료와 컨테이너 종료 코드를 확인한다. 기본 대기 시간은 3,600초이며 `WAIT_TIMEOUT`으로 조정한다. 대기 시간 초과나 터미널 연결 종료는 태스크 중지가 아니므로 출력된 ARN으로 명시적으로 중지한다. OCI 실행은 전경에서 진행되며 Ctrl+C로 중지한다.

일반 시나리오는 같은 실행기에 `scenarios/...js` 경로를 전달한다. OCI의 dev/prod 일반 시나리오는 `BASE_URL`을 반드시 지정하고, AWS는 Terraform 출력의 주소를 사용한다. 실제 부하 실행은 후속 작업이다.

Grafana에서 k6 Prometheus 대시보드를 열고 실행 시각과 `testid`를 선택한다. Prometheus에서는 아래와 같이 확인한다. 종료 후에는 순간 조회의 유효 시간이 지나 지표가 안 보일 수 있으므로 실행 시각이 포함된 범위를 사용한다.

```promql
max_over_time(k6_http_reqs_total{testid="aws-read-219"}[1h])
```

단일 읽기 확인의 요청 수는 1이어야 한다. CloudWatch에서 해당 k6 로그 스트림의 종료 요약을 함께 확인하고, ECS에서 해당 ARN이 `STOPPED`인지 확인한다. 실행 중인 k6 태스크가 남았다면 출력된 ARN별로 중지한다.

## 접근 범위와 복구

임베딩 8000번 포트는 공개하지 않는다. AWS에서는 Product와 k6 보안 그룹에서만 접근하고, Prometheus 9090번은 k6 보안 그룹으로만 추가 접근을 허용한다. Grafana는 기존 SSM 접근을 유지한다.

임베딩 배포가 실패하면 기존 정상 이미지 및 태스크 정의로 되돌리고 Product의 연결 주소를 함께 확인한다. OCI에서는 기존 배포 스크립트의 이전 릴리스 복구를 사용한다. 새 이미지나 이전 릴리스 스냅샷을 수동 삭제해 복구 수단을 없애지 않는다.

k6 지표가 없으면 먼저 요청의 `TESTID`, `K6_PROMETHEUS_RW_SERVER_URL`, Prometheus의 remote write 수신 설정과 보안 그룹을 확인한다. 테스트 대상 서비스 실패와 결과 수집 실패를 구분한다.

이 문서의 환경 준비 완료는 부하테스트 통과를 의미하지 않는다. 실제 부하 시나리오, 부하 단계, 중단 기준과 테스트 데이터 정리는 후속 테스트 계획에서 확정한다.

## 구현 검증 기록

2026-09-18 로컬 ARM64 Docker에서 모델을 포함한 이미지의 네트워크 없는 기동과 추론을 확인했다. 최종 k6 스크립트는 읽기 요청 1회(`cc-219-final-read`)와 임베딩 요청 1회(`cc-219-final-embed`)를 각각 수행했고 종료 코드는 0이었다. 두 실행의 요청 수 1을 Grafana의 Prometheus 데이터 소스 조회로 확인했다. 검증용 컨테이너는 작업 종료 시 제거하며 해당 지표는 운영 환경의 검증 기록이 아니다.

이미지 해시, 배포 명세, 이미지 보존/정리, dev 배포 복구 회귀 테스트와 Compose 설정 검사가 통과했다. AWS Terraform 3개 스택의 `validate`, runtime의 기동/정지 mock 테스트 2개, 관측 SSM 템플릿 검사도 통과했다.

실제 클라우드의 배포 버전, dev 자원 여유, Product DB 상태, 실제 state 기반 plan 및 두 환경의 배포 후 연결 확인은 아직 수행하지 않았다. AWS CLI 세션 갱신 후 실제 plan을 검토하고 적용해야 한다. 최대 처리량과 적정 사양은 미측정이다.
