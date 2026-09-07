package com.sub9.orderservice.order.application.port.output;

import java.time.Instant;
import java.util.UUID;

public interface PaymentCancellationPort {

    /**
     * 주문 행 잠금을 확보한 호출자의 로컬 트랜잭션에 참여해 전체 결제 취소를 저장합니다.
     * 구현체는 같은 데이터베이스와 트랜잭션을 사용하고, 실패 시 예외로 전체 변경을 롤백해야 합니다.
     * 성공 결제액 전체를 CUSTOMER_REQUEST 사유로 취소하며 원래 SUCCESS 결과는 유지합니다.
     * 결제별 취소와 취소 명령은 각각 한 번만 저장하며, 별도 트랜잭션이나 원격 호출로 구현하지 않습니다.
     */
    void cancel(UUID orderId, UUID commandRequestId, Instant canceledAt);
}
