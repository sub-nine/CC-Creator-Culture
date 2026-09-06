package com.sub9.orderservice;

import com.sub9.orderservice.cart.application.service.CartService;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "management.tracing.export.enabled=false"
})
@MockitoBean(types = {
        CartService.class,
        CouponApplicationPort.class,
        CouponUsagePort.class,
        StockPort.class
})
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
