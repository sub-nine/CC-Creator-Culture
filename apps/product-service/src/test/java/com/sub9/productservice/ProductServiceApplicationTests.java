package com.sub9.productservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "management.tracing.export.enabled=false"
})
class ProductServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
