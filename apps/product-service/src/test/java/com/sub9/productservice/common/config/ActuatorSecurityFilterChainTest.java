package com.sub9.productservice.common.config;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ActuatorSecurityFilterChainTest.ActuatorProbeController.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = "management.server.port=9090")
@Import({
  SecurityConfig.class,
  ActuatorSecurityConfig.class,
  ActuatorSecurityFilterChainTest.ActuatorProbeController.class
})
@DisplayName("Product Actuator 보안")
class ActuatorSecurityFilterChainTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("prod 9090에서 health와 readiness는 인증 없이 허용한다")
  void when_prod_management_port_health_and_readiness_are_public() throws Exception {
    mockMvc
        .perform(get("/actuator/health").with(managementPort()))
        .andExpect(status().isOk())
        .andExpect(content().string("UP"));
    mockMvc
        .perform(get("/actuator/health/readiness").with(managementPort()))
        .andExpect(status().isOk())
        .andExpect(content().string("READY"));
  }

  @Test
  @DisplayName("prod 9090에서 prometheus 수집을 허용한다")
  void when_prod_management_port_prometheus_is_allowed() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus").with(managementPort()))
        .andExpect(status().isOk())
        .andExpect(content().string("metrics"));
  }

  @Test
  @DisplayName("prod 9090에서 허용 목록 밖의 Actuator 엔드포인트는 401 또는 403으로 거부한다")
  void when_prod_management_port_other_actuator_endpoint_is_denied() throws Exception {
    mockMvc
        .perform(get("/actuator/env").with(managementPort()))
        .andExpect(status().is(anyOf(is(401), is(403))));
    mockMvc
        .perform(get("/actuator/beans").with(managementPort()))
        .andExpect(status().is(anyOf(is(401), is(403))));
  }

  private static RequestPostProcessor managementPort() {
    return request -> {
      request.setScheme("http");
      request.setServerName("localhost");
      request.setServerPort(9090);
      return request;
    };
  }

  @RestController
  static class ActuatorProbeController {

    @GetMapping("/actuator/health")
    String health() {
      return "UP";
    }

    @GetMapping("/actuator/health/readiness")
    String readiness() {
      return "READY";
    }

    @GetMapping("/actuator/prometheus")
    String prometheus() {
      return "metrics";
    }

    @GetMapping("/actuator/env")
    String env() {
      return "env";
    }

    @GetMapping("/actuator/beans")
    String beans() {
      return "beans";
    }
  }
}
