# AWS runtime 스택

NAT 1개, ALB HTTPS, MSK, Redis, Fargate 서비스 6개를 관리한다. persistent_config 는 persistent 스택 출력 객체를 그대로 넣는다.

서비스 desired_count 초기값은 0이다. 이미지가 있어도 컨트롤러가 UpdateService 하기 전에는 기동하지 않는다. Terraform은 task_definition과 desired_count를 ignore_changes 한다.

헬스체크는 관리 포트 9090의 /actuator/health/readiness 다. ALB는 Gateway 트래픽 8080, 헬스 9090이다. 해당 보안 그룹 규칙은 persistent 스택이 연다.

## Redis ACL

Terraform은 Redis 비밀번호를 읽거나 쓰지 않는다. redis_user_group_id 는 필수다. CLI로 default 사용자를 끄고 app 사용자와 user group을 만든 뒤, 비밀번호는 Secrets Manager cc-test/redis 에만 넣고 그 group id를 이 변수로 전달한다. IAM Redis 인증은 쓰지 않는다.

## DNS

api.nodyy.com 은 AWS 시험용이며 런타임 스택과 함께 만들고 정리한다. 이미 명시 레코드가 있으면 import 한다. wildcard 여부는 여기서 확인하지 않는다. dev.nodyy.com 은 건드리지 않는다.

## MSK

kafka_version 기본값은 3.9.x 다. 이 계정은 MSK 구독이 없어 지원 버전 API로 사전 확인하지 못했다. 토픽 ACL은 이 스택이 만들지 않는다.

## IAM 공백

bootstrap 의 runtime-deploy 역할은 persistent VPC/RDS/IAM/KMS 생성 권한이 없다. AdministratorAccess 는 넣지 않는다. persistent 적용용 infrastructure_apply_role 은 별도 작업이다.

상태 버킷이 생기기 전에는 로컬 state로 init 한다. 이후 backend.tf.example을 복사해 S3로 옮긴다.
