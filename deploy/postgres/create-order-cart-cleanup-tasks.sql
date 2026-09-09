-- Order Service가 사용하는 DB와 스키마에서 애플리케이션 배포 전에 실행합니다.
-- 기존 결제 주문의 작업은 생성하지 않습니다. 롤백 시 테이블과 미완료 작업은 유지합니다.
CREATE TABLE p_order_cart_cleanup_tasks (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_cart_cleanup_due ON p_order_cart_cleanup_tasks (next_attempt_at, id);
