package com.sub9.orderservice.order.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.application.port.output.StockOperationUncertainException;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = ProductStockHttpTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "management.tracing.export.enabled=false",
        "spring.cloud.openfeign.client.config.productStock.readTimeout=200"
})
class ProductStockHttpTest {
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final BlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();
    private static final AtomicInteger requestCount = new AtomicInteger();
    private static final HttpServer server = startServer();
    private static volatile int status = 204;
    private static volatile String body = "";
    private static volatile CountDownLatch responseGate = new CountDownLatch(0);

    @Autowired private StockPort stockPort;
    @Autowired private JsonMapper mapper;
    private final UUID orderId = UUID.randomUUID();
    private final List<StockItem> items = List.of(
            new StockItem(UUID.randomUUID(), 2), new StockItem(UUID.randomUUID(), 3));

    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    @EnableFeignClients(clients = ProductStockClient.class)
    @Import(ProductStockAdapter.class)
    static class Application {}

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.productStock.url",
                () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        requests.clear();
        requestCount.set(0);
        status = 204;
        body = "";
        responseGate = new CountDownLatch(0);
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204})
    void when_deducting_multiple_skus_one_json_request_is_sent(int successStatus) throws Exception {
        status = successStatus;
        stockPort.deduct(orderId, items);
        assertRequest("/deduct", null);
    }

    @ParameterizedTest
    @EnumSource(RestoreReason.class)
    void when_restoring_stock_reason_is_sent_as_enum_name(RestoreReason reason) throws Exception {
        stockPort.restore(orderId, items, reason);
        assertRequest("/restore", reason);
    }

    @ParameterizedTest
    @CsvSource({"409,STOCK_0002", "404,SKU_0004"})
    void when_http_failure_is_confirmed_business_error_is_returned(int errorStatus, String code) {
        status = errorStatus;
        body = "{\"errorCode\":\"" + code + "\",\"message\":\"오류\",\"errors\":[]}";
        assertThatThrownBy(() -> stockPort.deduct(orderId, items))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode().code()).isEqualTo(code));
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 404, 409, 500, 503})
    void when_http_result_is_unknown_no_retry_is_sent(int errorStatus) {
        status = errorStatus;
        body = "unrecognized-response";
        assertThatThrownBy(() -> stockPort.restore(orderId, items, RestoreReason.PAYMENT_FAILED))
                .isInstanceOf(StockOperationUncertainException.class);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void when_response_times_out_result_is_uncertain_without_retry() {
        responseGate = new CountDownLatch(1);
        try {
            assertThatThrownBy(() -> stockPort.deduct(orderId, items))
                    .isInstanceOf(StockOperationUncertainException.class);
            assertThat(requestCount.get()).isEqualTo(1);
        } finally {
            responseGate.countDown();
        }
    }

    private void assertRequest(String operation, RestoreReason reason) throws Exception {
        CapturedRequest request = requests.poll(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/internal/v1/stocks" + operation);
        assertThat(request.contentType()).startsWith("application/json");
        var expected = mapper.createObjectNode();
        expected.put("orderId", orderId.toString());
        expected.set("items", mapper.valueToTree(items));
        if (reason != null) {
            expected.put("reason", reason.name());
        }
        assertThat(mapper.readTree(request.body())).isEqualTo(expected);
        assertThat(requestCount.get()).isEqualTo(1);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                int responseStatus = status;
                byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
                CountDownLatch gate = responseGate;
                requestCount.incrementAndGet();
                requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                try (exchange) {
                    try {
                        gate.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    // Retry-After가 있어도 재고 요청을 자동으로 다시 보내지 않아야 합니다.
                    if (responseStatus == 503) exchange.getResponseHeaders().set("Retry-After", "0");
                    exchange.sendResponseHeaders(responseStatus, responseBody.length == 0 ? -1 : responseBody.length);
                    if (responseBody.length > 0) exchange.getResponseBody().write(responseBody);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP 테스트 서버를 시작할 수 없습니다.", exception);
        }
    }

    private record CapturedRequest(String method, String path, String contentType, String body) {}
}
