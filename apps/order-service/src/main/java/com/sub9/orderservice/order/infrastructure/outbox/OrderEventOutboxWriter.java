package com.sub9.orderservice.order.infrastructure.outbox;

import com.sub9.common.identifier.UuidV7Generator;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class OrderEventOutboxWriter {
    private final OrderEventOutboxJpaRepository outbox;
    private final UuidV7Generator uuidGenerator;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    // 호출한 트랜잭션에 함께 커밋되어야 하므로 트랜잭션 밖에서는 기록하지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, String key, Object event) {
        outbox.save(new OrderEventOutbox(
                uuidGenerator.generate(), topic, key, jsonMapper.writeValueAsString(event), clock.instant()));
    }
}
