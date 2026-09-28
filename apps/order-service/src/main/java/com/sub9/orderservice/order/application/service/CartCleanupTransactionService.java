package com.sub9.orderservice.order.application.service;

import com.sub9.orderservice.order.application.port.output.CartCleanupCommand;
import com.sub9.orderservice.order.application.port.output.CartCleanupPort;
import com.sub9.orderservice.order.domain.repository.CartCleanupTaskRepository;
import java.time.Instant;
import java.util.List;
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
    public boolean process(UUID taskId, Instant now) {
        return tasks.findDueForUpdate(taskId, now).map(task -> {
            CartCleanupCommand command = jsonMapper.readValue(task.getPayload(), CartCleanupCommand.class);
            cleanup.cleanup(command);
            // 같은 DB의 삭제와 작업 완료를 함께 커밋하여 중단 후에도 안전하게 재처리한다.
            tasks.delete(task);
            return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Instant> processBatch(Instant now, int limit) {
        var claimed = tasks.claimDue(now, limit);
        for (var task : claimed) {
            cleanup.cleanup(jsonMapper.readValue(task.getPayload(), CartCleanupCommand.class));
        }
        // 한 연결에서 배치 전체의 장바구니 삭제와 작업 완료를 함께 커밋하거나 함께 롤백한다.
        tasks.deleteAll(claimed);
        return claimed.stream().map(task -> task.getCreatedAt()).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postpone(UUID taskId, Instant nextAttemptAt) {
        tasks.postpone(taskId, nextAttemptAt);
    }
}
