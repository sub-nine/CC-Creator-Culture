# runtime deployment_config 인터페이스

런타임 스택 출력 deployment_config는 객체다. 워크플로는 terraform output -json deployment_config 로 읽는다. AWS 자원 ID는 적용 후에만 채워지며 예시나 코드에 가짜 ID를 넣지 않는다.

서비스 키는 config-server, eureka-server, gateway, user-service, product-service, order-service 다. DB 키는 user-service, product-service, order-service 다.

형태:

- region: ap-northeast-2
- cluster: ECS 클러스터 이름
- services: 서비스 키 -> ECS 서비스 이름
- service_task_families: 서비스 키 -> 태스크 패밀리
- subnets: 사설 서브넷 ID 목록
- security_groups: app, alb, rds, observation, msk, redis
- release_bucket: 릴리스 버킷 이름
- rds_instances: DB 키 -> 인스턴스 식별자
- observation_instance_id
- msk_arn
- redis_id
- target_group_arn
- db_endpoints: DB 키 -> 호스트 이름
- secret_arns: rds_master(DB 키), app(DB 키), jwt, kafka, redis. ARN만.

## 태스크 정의 소유

Terraform 런타임은 최초 태스크 정의와 ECS 서비스만 만든다. service_images는 6개 서비스 모두 repository@sha256: 64 hex digest여야 한다. desired_count 초기값은 0이다. launchType은 지정하지 않고 FARGATE capacity provider만 사용한다.

이후 이미지 교체와 태스크 수는 릴리스 런타임 컨트롤러가 RegisterTaskDefinition과 UpdateService로 소유한다. Terraform은 aws_ecs_service의 task_definition, desired_count와 aws_ecs_task_definition의 container_definitions를 ignore_changes 한다.

선택 매니페스트는 부트스트랩 릴리스 버킷 키 selected/manifest.json 이다. 본문은 ignore_changes, 삭제는 prevent_destroy라 런타임 스택을 지워도 남는다.
