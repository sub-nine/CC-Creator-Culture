# AWS persistent 스택

서울 리전 VPC, 서비스별 보안 그룹, RDS 3개, 빈 시크릿, Cloud Map, 로그, ECS 역할, 모니터링 EC2와 Kafka EC2를 관리한다. RDS와 두 EC2의 전원(running/stopped)은 런타임 스택이 `app_running` 으로 소유한다. NAT, ALB, Redis, ECS 서비스는 만들지 않는다. 사설 라우트에 NAT 경로는 넣지 않는다. 런타임이 붙인다.

적용은 저장된 plan으로만 한다. 이 변경에서는 apply하지 않는다.

부트스트랩 상태 버킷이 생긴 뒤 `terraform init -backend=false`로 검증하고, 적용 전에는 `backend.tf.example`을 `backend.tf`로 복사한 다음 `backend.hcl`의 `use_lockfile = true`로 붙인다.

시크릿 값은 만들지 않는다. RDS 마스터는 AWS 관리 시크릿이고 앱이 그 username/password 를 DB 자격 증명으로 그대로 쓴다. 별도 앱 DB 시크릿은 없다. JWT/Redis는 `cc-test/...` 이름으로 시크릿만 만든다. Kafka는 VPC 내부에서 PLAINTEXT를 쓴다. 브로커 시크릿이 없다.

모니터링 인스턴스와 Kafka 인스턴스 전원은 런타임 `aws_ec2_instance_state` 가 정한다. NAT가 생긴 뒤 워크플로가 SSM 문서 `cc-test-start-observation`과 `cc-test-start-kafka`를 보낸다. userdata로 패키지를 설치하지 않는다. 모니터링 문서는 Docker가 없으면 설치하고 고정 ARM 이미지 세 개를 올린 뒤 헬스 URL을 기다린다. Kafka 문서는 compose와 같은 KRaft 이미지를 올리고 9092를 기다린다. Kafka는 t4g.small, 데이터는 20GB gp3(`/var/lib/kafka`)다.

Grafana는 127.0.0.1:3000이라 외부에 열리지 않는다. SSM 포트 포워드를 쓴다. 관리자 비밀번호는 `cc-test/grafana` JSON `{username,password}`를 인스턴스가 읽고, Terraform 상태 파일(state)에는 값이 없다. Zipkin heap은 256m이다.

시드 비밀번호 해시는 `cc-test/seed` JSON `{password_hash}` 시크릿만 만든다. 값은 넣지 않는다. ECS execution role이 읽을 수 있고, 주입은 seed 일회 작업만 한다.

Product 이미지는 기존 S3 버킷과 CloudFront를 쓴다. 두 리소스는 Terraform 밖에서 관리하고, 여기서는 `product_image_bucket`(기본값 `cc-creator-culture`)과 `product_image_public_url` 변수로 이름과 URL만 받는다. 정적 키 대신 product-service 전용 Task Role `cc-test-product-service-task`가 `products/images/*` 경로의 읽기, 쓰기, 삭제와 같은 경로의 목록 조회만 허용한다. 목록 조회 권한이 있어야 없는 객체를 조회할 때 S3가 403 대신 404를 돌려주고, 앱이 업로드 누락 오류로 처리할 수 있다. 다른 서비스는 기존 `cc-test-ecs-task` 역할을 그대로 쓴다.

이전 R2용 `cc-test/product-r2` 시크릿은 `removed` 블록으로 Terraform 관리에서만 뺐고 AWS에는 남아 있다. 어떤 배포도 이 시크릿을 읽지 않는 것을 확인한 뒤 수동으로 삭제한다.

런타임 입력은 `terraform output -json persistent_config` 객체 하나다.
