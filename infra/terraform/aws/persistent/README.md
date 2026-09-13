# AWS persistent 스택

서울 리전 VPC, 서비스별 보안 그룹, RDS 3개, 빈 시크릿 그릇, Cloud Map, 로그, ECS 역할, 정지된 관측 EC2를 관리한다. NAT, ALB, MSK, Redis, ECS 서비스는 만들지 않는다. 사설 라우트에 NAT 경로는 넣지 않는다. 런타임이 붙인다.

적용은 저장된 plan으로만 한다. 이 변경에서는 apply하지 않는다.

부트스트랩 상태 버킷이 생긴 뒤 `terraform init -backend=false`로 검증하고, 적용 전에는 `backend.tf.example`을 `backend.tf`로 복사한 다음 `backend.hcl`의 `use_lockfile = true`로 붙인다.

시크릿 값은 만들지 않는다. RDS 마스터는 AWS 관리 시크릿이고, 앱/JWT/Redis 그릇은 `cc-test/...` 이름만 만든다. MSK SASL/SCRAM 그릇은 `AmazonMSK_cc-test_<서비스>`이고 고객 관리 KMS로 암호화한다. username/password JSON은 Terraform 밖에서 채운다.

관측 노드는 처음 만들어지면 바로 정지한다. NAT가 생긴 뒤 런타임이 SSM 문서 `cc-test-start-observation`을 보낸다. userdata로 패키지를 깔지 않는다. 문서는 Docker가 없으면 설치하고, 고정 ARM 이미지 세 개를 올린 뒤 헬스 URL을 기다린다.

Grafana는 127.0.0.1:3000이라 외부에 열리지 않는다. SSM 포트 포워드를 쓴다. 관리자 비밀번호는 `cc-test/grafana` JSON `{username,password}`를 인스턴스가 읽고, Terraform state에는 값이 없다. Zipkin heap은 256m이다.

시드 비밀번호 해시는 `cc-test/seed` JSON `{password_hash}` 그릇만 만든다. 값은 넣지 않는다. ECS execution role이 읽을 수 있고, 주입은 seed 일회 작업만 한다.

Product 이미지에 필요한 R2 연결은 `cc-test/product-r2` JSON `{access_key,secret_key,endpoint,bucket,public_url}` 그릇만 만든다. 값은 넣지 않는다. 실제 R2 연결 정보는 아직 확정되지 않았고, live 기동 때 필수 입력이다. 기존 Product 기능은 끄거나 mock으로 바꾸지 않는다.

런타임 입력은 `terraform output -json persistent_config` 객체 하나다.
