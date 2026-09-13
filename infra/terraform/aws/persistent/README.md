# AWS persistent 스택

서울 리전 VPC, 서비스별 보안 그룹, RDS 3개, 빈 시크릿 그릇, Cloud Map, 로그, ECS 역할, 관측 EC2와 Kafka EC2를 관리한다. RDS와 두 EC2의 전원(running/stopped)은 런타임 스택이 `app_running` 으로 소유한다. NAT, ALB, Redis, ECS 서비스는 만들지 않는다. 사설 라우트에 NAT 경로는 넣지 않는다. 런타임이 붙인다.

적용은 저장된 plan으로만 한다. 이 변경에서는 apply하지 않는다.

부트스트랩 상태 버킷이 생긴 뒤 `terraform init -backend=false`로 검증하고, 적용 전에는 `backend.tf.example`을 `backend.tf`로 복사한 다음 `backend.hcl`의 `use_lockfile = true`로 붙인다.

시크릿 값은 만들지 않는다. RDS 마스터는 AWS 관리 시크릿이고 앱이 그 username/password 를 DB 자격 증명으로 그대로 쓴다. 별도 앱 DB 시크릿은 없다. JWT/Redis 그릇은 `cc-test/...` 이름만 만든다. Kafka는 VPC 안 PLAINTEXT라 브로커 시크릿이 없다.

관측 노드와 Kafka 노드 전원은 런타임 `aws_ec2_instance_state` 가 정한다. NAT가 생긴 뒤 워크플로가 SSM 문서 `cc-test-start-observation`과 `cc-test-start-kafka`를 보낸다. userdata로 패키지를 깔지 않는다. 관측 문서는 Docker가 없으면 설치하고 고정 ARM 이미지 세 개를 올린 뒤 헬스 URL을 기다린다. Kafka 문서는 compose와 같은 KRaft 이미지를 올리고 9092를 기다린다. Kafka는 t4g.small, 데이터는 20GB gp3(`/var/lib/kafka`)다.

Grafana는 127.0.0.1:3000이라 외부에 열리지 않는다. SSM 포트 포워드를 쓴다. 관리자 비밀번호는 `cc-test/grafana` JSON `{username,password}`를 인스턴스가 읽고, Terraform state에는 값이 없다. Zipkin heap은 256m이다.

시드 비밀번호 해시는 `cc-test/seed` JSON `{password_hash}` 그릇만 만든다. 값은 넣지 않는다. ECS execution role이 읽을 수 있고, 주입은 seed 일회 작업만 한다.

Product 이미지에 필요한 R2 연결은 `cc-test/product-r2` JSON `{access_key,secret_key,endpoint,bucket,public_url}` 그릇만 만든다. 값은 넣지 않는다. 실제 R2 연결 정보는 아직 확정되지 않았고, live 기동 때 필수 입력이다. 기존 Product 기능은 끄거나 mock으로 바꾸지 않는다.

런타임 입력은 `terraform output -json persistent_config` 객체 하나다.
