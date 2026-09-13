# AWS 서비스 DB 마이그레이션

User, Product, Order 엔티티를 Flyway SQL로 고정한다. 실행 시 git clone 하지 않고 이미지에 복사된 버전만 적용한다.

user DB는 private 스키마에 p_users, p_creators, 알림 테이블을 둔다. product/order 는 public 이다. 코드에 pgvector 컬럼이 없어 확장은 만들지 않는다.

사용 방법은 INTERFACE.md 를 본다. 앱 시작 훅에 연결하지 말고 일회성 작업으로 실행한다.
