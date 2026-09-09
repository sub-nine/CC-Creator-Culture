package com.sub9.orderservice.order.application.service;

import com.sub9.orderservice.order.application.port.output.CartCleanupCommand;
import com.sub9.orderservice.order.application.port.output.CartCleanupPort;
import com.sub9.orderservice.order.domain.repository.CartCleanupTaskRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class CartCleanupTransactionService {
    private final CartCleanupTaskRepository tasks;
    private final CartCleanupPort cleanup;
    private final JsonMapper jsonMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID taskId, Instant now) {
        tasks.findDueForUpdate(taskId, now).ifPresent(task -> {
            CartCleanupCommand command = jsonMapper.readValue(task.getPayload(), CartCleanupCommand.class);
            cleanup.cleanup(command);
            // 같은 DB의 삭제와 작업 완료를 함께 커밋하여 중단 후에도 안전하게 재처리한다.
            tasks.delete(task);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postpone(UUID taskId, Instant nextAttemptAt) {
        tasks.postpone(taskId, nextAttemptAt);
    }
}
