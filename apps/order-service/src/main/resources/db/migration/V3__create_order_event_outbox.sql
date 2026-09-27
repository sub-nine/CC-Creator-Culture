-- 결제 트랜잭션 안에서 Kafka로 보낼 이벤트를 함께 저장하고, 발행기가 전송 성공을 확인한 뒤 지운다.
CREATE TABLE p_order_event_outbox (
    id uuid PRIMARY KEY,
    topic varchar(100) NOT NULL,
    message_key varchar(100) NOT NULL,
    payload text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL
);

CREATE INDEX idx_order_event_outbox_due ON p_order_event_outbox (next_attempt_at, id);
