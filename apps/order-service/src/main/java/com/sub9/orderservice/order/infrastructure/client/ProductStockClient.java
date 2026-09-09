package com.sub9.orderservice.order.infrastructure.client;

import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import feign.Retryer;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", contextId = "productStock",
        path = "/internal/v1/stocks", configuration = ProductStockClient.Configuration.class)
public interface ProductStockClient {

    @PostMapping("/deduct")
    void deduct(@RequestBody DeductRequest request);

    @PostMapping("/restore")
    void restore(@RequestBody RestoreRequest request);

    record DeductRequest(UUID orderId, List<StockItem> items) {}

    record RestoreRequest(UUID orderId, List<StockItem> items, RestoreReason reason) {}

    // 재고 클라이언트에만 적용하며 전체 애플리케이션의 설정으로 등록하지 않습니다.
    class Configuration {
        @Bean
        public Retryer stockRetryer() {
            return Retryer.NEVER_RETRY;
        }
    }
}
