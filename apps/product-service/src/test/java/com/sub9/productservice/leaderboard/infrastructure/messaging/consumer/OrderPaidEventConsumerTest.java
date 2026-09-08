package com.sub9.productservice.leaderboard.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sub9.common.kafka.event.OrderPaidEvent;
import com.sub9.productservice.leaderboard.application.model.ProductQuantity;
import com.sub9.productservice.leaderboard.application.port.in.RecordOrderScoreUseCase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

@DisplayName("주문 결제 완료 이벤트 수신")
class OrderPaidEventConsumerTest {

    private final RecordOrderScoreUseCase useCase = mock(RecordOrderScoreUseCase.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final OrderPaidEventConsumer consumer = new OrderPaidEventConsumer(useCase);

    @Test
    @DisplayName("상품별 총수량을 전달하고 점수 반영이 완료되면 수신을 확인한다")
    void when_consumed_product_totals_are_forwarded_before_acknowledgment() {
        UUID orderId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        OrderPaidEvent event = new OrderPaidEvent(orderId, Map.of(first, 5L, second, 2L));

        consumer.consume(event, ack);

        ArgumentCaptor<List<ProductQuantity>> quantities = ArgumentCaptor.forClass(List.class);
        var sequence = inOrder(useCase, ack);
        sequence.verify(useCase).recordOrderScore(eq(orderId), quantities.capture());
        assertThat(quantities.getValue()).containsExactlyInAnyOrder(
                new ProductQuantity(first, 5L), new ProductQuantity(second, 2L));
        sequence.verify(ack).acknowledge();
    }

    @Test
    @DisplayName("점수 반영에 실패하면 수신을 확인하지 않는다")
    void when_score_update_fails_message_is_not_acknowledged() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        doThrow(new IllegalStateException("score update failed"))
                .when(useCase).recordOrderScore(eq(orderId), anyList());

        assertThatThrownBy(() -> consumer.consume(
                new OrderPaidEvent(orderId, Map.of(productId, 5L)), ack))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(ack);
    }
}
