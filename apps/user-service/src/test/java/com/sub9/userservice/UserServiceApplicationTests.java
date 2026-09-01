package com.sub9.userservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("PostgreSQL 통합 테스트에서 애플리케이션 컨텍스트를 검증한다")
@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:user-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=none",
		"management.tracing.export.enabled=false"
})
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
