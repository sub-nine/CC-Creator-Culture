package com.sub9.orderservice.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.common.kafka.event.OrderPaidEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.orderservice.order.application.port.input.PaymentResultUseCase;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "order.cart-cleanup.enabled=false",
        "management.tracing.export.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("결제 이벤트 Kafka 발행 통합 테스트")
class OrderPaymentEventKafkaIntegrationTest extends AbstractIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-27T00:00:00Z");
    private static final List<String> TOPICS = List.of(KafkaTopics.ORDER_PAID, KafkaTopics.ORDER_NOTIFICATION);

    // 로컬 Compose와 같은 브로커 이미지를 사용한다.
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.2");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @TestConfiguration
    static class KafkaTestConfig {

        @Bean
        NewTopic orderPaidTopic() {
            return TopicBuilder.name(KafkaTopics.ORDER_PAID).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic orderNotificationTopic() {
            return TopicBuilder.name(KafkaTopics.ORDER_NOTIFICATION).partitions(1).replicas(1).build();
        }
    }

    @Autowired
    private PaymentResultUseCase paymentResultUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    private final UuidV7Generator uuids = new UuidV7Generator();
    private Map<TopicPartition, Long> startOffsets;

    @BeforeEach
    void recordStartOffsets() {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            startOffsets = consumer.endOffsets(TOPICS.stream().map(topic -> new TopicPartition(topic, 0)).toList());
        }
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from p_order_items");
        jdbcTemplate.update("delete from p_orders");
    }

    @Test
    @DisplayName("브로커가 정상일 때 결제를 완료하면 결제 이벤트와 알림 이벤트가 브로커에 기록된다")
    void when_broker_is_available_payment_completion_publishes_events() {
        Order order = saveOrder();

        paymentResultUseCase.markPaid(order.getId(), order.getExpiresAt().minusSeconds(1));

        List<ConsumerRecord<String, String>> paid = recordsUntil(KafkaTopics.ORDER_PAID, order.getId());
        List<ConsumerRecord<String, String>> notification = recordsUntil(KafkaTopics.ORDER_NOTIFICATION, order.getId());
        assertThat(keys(notification)).contains(order.getId().toString());
        String paidPayload = paid.stream()
                .filter(record -> record.key().equals(order.getId().toString()))
                .findFirst()
                .orElseThrow()
                .value();
        assertThat(jsonMapper.readValue(paidPayload, OrderPaidEvent.class).orderId()).isEqualTo(order.getId());
    }

    // 시작 오프셋부터 읽어 해당 주문의 레코드가 나올 때까지 모은다. 나오지 않으면 10초 뒤 실패한다.
    private List<ConsumerRecord<String, String>> recordsUntil(String topic, UUID orderId) {
        TopicPartition partition = new TopicPartition(topic, 0);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.assign(List.of(partition));
            consumer.seek(partition, startOffsets.get(partition));
            await().pollInSameThread().atMost(Duration.ofSeconds(10)).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
                return keys(records).contains(orderId.toString());
            });
        }
        return records;
    }

    private static List<String> keys(List<ConsumerRecord<String, String>> records) {
        return records.stream().map(ConsumerRecord::key).toList();
    }

    private static KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    // 장바구니 항목 없이 만든 주문이라 결제 완료 시 장바구니 정리 작업이 생기지 않는다.
    private Order saveOrder() {
        return orderRepository.save(Order.create(
                uuids.generate(),
                uuids.generate(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "101호"),
                List.of(OrderItem.create(
                        uuids.generate(), null, uuids.generate(), uuids.generate(), uuids.generate(), null,
                        ProductSnapshot.of("아크릴 스탠드", "A 타입", Money.won(18_000), 1),
                        Money.won(0))),
                CREATED_AT));
    }
}
