# AWS 시험 환경 Terraform

서울 리전 시험 환경의 부트스트랩 스택이다. 상태 버킷, 릴리스 버킷, ECR 6개, GitHub OIDC 역할만 이 디렉터리에서 관리한다.

적용은 저장된 plan으로만 한다. 이 변경에서는 apply하지 않는다.

상태 버킷이 생기기 전에는 원격 백엔드를 붙이지 않는다. bootstrap 디렉터리에서 기본 로컬 state로 init 하고 첫 apply를 한다. 버킷이 만들어진 뒤 backend.tf.example을 backend.tf로 복사하고 backend.hcl.example을 backend.hcl로 채운 다음 `terraform init -migrate-state -backend-config=backend.hcl` 로 S3 백엔드로 옮긴다. use_lockfile을 사용하고 백엔드 블록에 변수를 넣지 않는다.

plan-read와 runtime-deploy 역할은 GitHub Environment production, audience sts.amazonaws.com만 신뢰한다. 이미지 게시 역할만 Environment development를 신뢰한다. 기존 OCI 배포의 development 환경과 섞지 않는다.

선택 매니페스트 객체는 Terraform이 만들지 않는다. 실제 성공한 배포만 컨트롤러가 릴리스 버킷 키 selected/manifest.json 에 기록한다.

시크릿 값과 계정 ID는 예시에 넣지 않는다. 버킷 이름은 적용 입력으로 받는다.
