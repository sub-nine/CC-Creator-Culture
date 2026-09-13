# AWS runtime 스택

NAT 1개, ALB HTTPS, Redis, Fargate 서비스 6개, 태스크 정의, 실행 상태를 관리한다. Kafka 브로커는 persistent의 t4g.small EC2다. persistent_config 는 persistent 스택 출력 객체를 그대로 넣는다.

## 입력과 출력

- `release_sha`: 이 apply를 만든 40자 커밋 SHA. 추적용이며 이미지 태그로 쓰지 않는다.
- `image_tags`: 서비스 6개와 db-seed의 콘텐츠 해시 태그. `${ecr_repository_urls[name]}:${image_tags[name]}`. ECR 태그는 IMMUTABLE이라 digest가 고정된다. 태그가 바뀐 서비스만 새 태스크 정의가 생긴다.
- `config_labels`: 서비스별 config-repo 라벨. config-server에는 `CONFIG_GIT_DEFAULT_LABEL`, 나머지에는 `SPRING_CLOUD_CONFIG_LABEL`로 전달한다. 라벨이 바뀐 서비스만 재시작된다.
- `app_running`: true면 서비스 desired_count 1, RDS available, 관측과 Kafka EC2 running. false면 모두 정지한다. 기본값 false.
- 출력: `release_sha`, `image_tags`, `config_labels`, `app_running`, `cluster_name`, `seed_task_families`(서비스 키 -> family 맵), `app_subnet_ids`, `migration_security_group_id`, `observation_instance_id`, `observation_bootstrap_document`, `kafka_instance_id`, `kafka_bootstrap_document`, `redis_user_group_id`.

ignore_changes 는 없다. 이미지 교체, 설정 라벨, desired_count, 전원은 모두 `terraform apply` 한 번으로 반영된다.

## 기동 순서

ECS 서비스는 `platform`(config-server, eureka-server) -> `app`(user, product, order) -> `gateway` 순으로 `depends_on` 이 걸려 있고 `wait_for_steady_state = true` 라 앞 그룹이 안정되기 전에는 다음 그룹을 만들거나 바꾸지 않는다. 배포 실패는 circuit breaker rollback 이 되돌린다. `aws_appautoscaling_target` 이 서비스별 최소(desired_count)와 최대(config, eureka 1, order 6, 나머지 4)를 선언한다.

DB 자격 증명은 RDS 관리 마스터 시크릿(`secret_arns.rds_master`)의 username/password 키를 쓴다. 스키마 마이그레이션은 앱이 기동 시 Flyway로 실행하고, 별도 마이그레이션 태스크는 없다. 시드는 서비스별 `db-seed-<service>` 태스크 정의 3개가 있고(run-task override 로는 secrets 를 넣을 수 없어 정의마다 DB 접속 정보와 시크릿을 담는다), 워크플로가 필요할 때 `run-task` 로 한 번 실행한다.

헬스체크는 관리 포트 9090의 /actuator/health/readiness 다. ALB는 Gateway 트래픽 8080, 헬스 9090이다. 해당 보안 그룹 규칙은 persistent 스택이 연다.

## Redis ACL

Terraform은 Redis 비밀번호를 읽거나 쓰지 않는다. redis_user_group_id 는 필수이며 이름이 `cc-test-` 로 시작해야 runtime-deploy 역할의 IAM 자원 패턴에 맞는다. CLI로 default 사용자를 끄고 app 사용자와 user group을 만든 뒤, 비밀번호는 Secrets Manager cc-test/redis 에만 넣고 그 group id를 이 변수로 전달한다. IAM Redis 인증은 쓰지 않는다.

## DNS

api.nodyy.com 은 AWS 시험용이며 런타임 스택과 함께 만들고 정리한다. 이미 명시 레코드가 있으면 import 한다. wildcard 여부는 여기서 확인하지 않는다. dev.nodyy.com 은 건드리지 않는다.

## Kafka

브로커는 persistent의 t4g.small 1대다. 앱은 `kafka.<namespace>:9092` PLAINTEXT로 붙는다. SASL은 쓰지 않는다. 토픽은 브로커가 자동 생성하고 복제 계수는 1이다. 브로커를 늘리는 작업은 이 스택이 하지 않는다.

## IAM 공백

bootstrap 의 runtime-deploy 역할은 이 스택의 plan, apply, destroy 에 필요한 권한만 있고 persistent VPC/RDS/IAM/KMS 생성 권한은 없다. AdministratorAccess 는 넣지 않는다. bootstrap과 persistent 적용은 infrastructure_apply_role 이 한다.

상태 버킷이 생기기 전에는 로컬 state로 init 한다. 이후 backend.tf.example을 복사해 S3로 옮긴다.
