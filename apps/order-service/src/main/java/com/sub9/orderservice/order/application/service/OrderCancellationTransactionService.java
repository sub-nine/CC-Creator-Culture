package com.sub9.orderservice.order.application.service;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.order.presentation.response.CancelOrderResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCancellationTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentCancellationPort paymentCancellationPort;
    private final OrderCommandIdempotencyService idempotencyService;
    private final Clock clock;

    @Transactional
    public CanceledOrder cancel(UUID customerId, UUID commandRequestId, OrderNumber orderNumber) {
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        Instant canceledAt = clock.instant();
        order.cancel(customerId, canceledAt);
        paymentCancellationPort.cancel(order.getId(), commandRequestId, canceledAt);

        ApiResponse<CancelOrderResponse> responseBody = ApiResponse.success(
                "주문 취소 성공",
                new CancelOrderResponse(order.getOrderNumber().toString(), order.getStatus(), canceledAt));
        idempotencyService.completeSuccess(commandRequestId, order, 200, responseBody);
        StockRestoreCommand stockRestore = new StockRestoreCommand(
                order.getId(),
                order.getItems().stream()
                        .map(item -> new StockItem(item.getSkuId(), item.getProductSnapshot().getQuantity()))
                        .toList(),
                RestoreReason.ORDER_CANCEL);
        return new CanceledOrder(new OrderCancellationResult(200, responseBody), stockRestore);
    }

    public record CanceledOrder(OrderCancellationResult result, StockRestoreCommand stockRestore) {
    }
}
