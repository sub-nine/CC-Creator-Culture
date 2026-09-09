package com.sub9.orderservice.order.infrastructure.client;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.application.port.output.StockOperationUncertainException;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import feign.FeignException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ProductStockAdapter implements StockPort {

    private final ProductStockClient client;
    private final JsonMapper jsonMapper;

    @Override
    public void deduct(UUID orderId, List<StockItem> items) {
        List<StockItem> validated = validate(orderId, items);
        execute(() -> client.deduct(new ProductStockClient.DeductRequest(orderId, validated)));
    }

    @Override
    public void restore(UUID orderId, List<StockItem> items, RestoreReason reason) {
        List<StockItem> validated = validate(orderId, items);
        if (reason == null) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
        execute(() -> client.restore(new ProductStockClient.RestoreRequest(orderId, validated, reason)));
    }

    private static List<StockItem> validate(UUID orderId, List<StockItem> items) {
        if (orderId == null || items == null || items.isEmpty() || items.stream().anyMatch(item -> item == null)) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
        return List.copyOf(items);
    }

    private void execute(Runnable request) {
        try {
            request.run();
        } catch (FeignException exception) {
            ProductStockErrorCode error = confirmedError(exception);
            if (error != null) {
                throw new BusinessException(error);
            }
            throw new StockOperationUncertainException("Product 재고 처리 결과를 확인할 수 없습니다.", exception);
        }
    }

    private ProductStockErrorCode confirmedError(FeignException exception) {
        // HTTP 상태만으로 실패를 확정하면 응답 유실 후 주문을 잘못 종료할 수 있습니다.
        if (exception.status() != 404 && exception.status() != 409) {
            return null;
        }
        try {
            var body = jsonMapper.readTree(exception.contentUTF8());
            if (body == null || !body.path("errorCode").isString()) {
                return null;
            }
            String code = body.path("errorCode").asString();
            for (ProductStockErrorCode error : ProductStockErrorCode.values()) {
                if (exception.status() == error.status().value() && error.code().equals(code)) {
                    return error;
                }
            }
        } catch (JacksonException ignored) {
            // 해석할 수 없는 오류 응답은 처리 결과가 불확실한 상태로 유지합니다.
        }
        return null;
    }
}
