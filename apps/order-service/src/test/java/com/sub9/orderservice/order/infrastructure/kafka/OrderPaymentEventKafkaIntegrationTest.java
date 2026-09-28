package com.sub9.orderservice.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.common.kafka.event.OrderCanceledEvent;
import com.sub9.common.kafka.event.OrderPaidEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.orderservice.order.application.port.input.PaymentResultUseCase;
import com.sub9.orderservice.order.application.service.OrderCancellationService;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
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
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        // 브로커 장애 중 전송이 테스트 시간 안에 실패하도록 대기 시간을 줄인다.
        "spring.kafka.producer.properties[max.block.ms]=2000",
        "spring.kafka.producer.properties[request.timeout.ms]=1000",
        "spring.kafka.producer.properties[delivery.timeout.ms]=3000",
        "order.event-outbox.enabled=true",
        "order.event-outbox.interval-ms=200",
        // 브로커 복구 후 재발행을 테스트 시간 안에 확인하도록 재시도 간격을 줄인다.
        "order.event-outbox.retry-delay=1s",
        "order.cart-cleanup.enabled=false",
        "management.tracing.export.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("주문 이벤트 Kafka 발행 통합 테스트")
class OrderPaymentEventKafkaIntegrationTest extends AbstractIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-27T00:00:00Z");
    private static final List<String> PAYMENT_TOPICS = List.of(KafkaTopics.ORDER_PAID, KafkaTopics.ORDER_NOTIFICATION);
    private static final List<String> TOPICS = List.of(
            KafkaTopics.ORDER_PAID, KafkaTopics.ORDER_NOTIFICATION, KafkaTopics.ORDER_CANCELED);
    private static final ConcurrentLinkedQueue<String> SEND_FAILURES = new ConcurrentLinkedQueue<>();

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

        @Bean
        NewTopic orderCanceledTopic() {
            return TopicBuilder.name(KafkaTopics.ORDER_CANCELED).partitions(1).replicas(1).build();
        }

        // 기본 LoggingProducerListener 대신 전송 실패한 토픽과 키를 기록한다.
        @Bean
        ProducerListener<Object, Object> sendFailureRecorder() {
            return new ProducerListener<>() {
                @Override
                public void onError(ProducerRecord<Object, Object> record, RecordMetadata metadata,
                        Exception exception) {
                    SEND_FAILURES.add(failure(record.topic(), String.valueOf(record.key())));
                }
            };
        }
    }

    @Autowired
    private PaymentResultUseCase paymentResultUseCase;

    @Autowired
    private OrderCancellationService orderCancellationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProducerFactory<String, String> producerFactory;

    private final UuidV7Generator uuids = new UuidV7Generator();
    private Map<TopicPartition, Long> startOffsets;

    @BeforeEach
    void recordStartOffsets() {
        SEND_FAILURES.clear();
        try (KafkaConsumer<String, String> consumer = consumer()) {
            startOffsets = consumer.endOffsets(TOPICS.stream().map(topic -> new TopicPartition(topic, 0)).toList());
        }
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from p_order_event_outbox");
        jdbcTemplate.update("delete from p_order_command_requests");
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

    @Test
    @DisplayName("브로커 장애 중 결제가 커밋되면 Outbox에 남은 이벤트가 복구 후 발행된다")
    void when_broker_is_unavailable_at_payment_commit_events_are_published_after_recovery() {
        Order lost = saveOrder();
        Order recovered = saveOrder();

        // 기존 producer는 브로커 메타데이터를 캐시하고 있어, 정지 중 전송이 소켓 버퍼에 쌓였다가
        // 재개 후 기록될 수 있다. 새 producer를 쓰게 해 메타데이터 조회 단계에서 전송이 실패하도록 만든다.
        producerFactory.reset();
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            paymentResultUseCase.markPaid(lost.getId(), lost.getExpiresAt().minusSeconds(1));

            assertThat(jdbcTemplate.queryForObject(
                    "select status from p_orders where id = ?", String.class, lost.getId())).isEqualTo("PAID");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(SEND_FAILURES).contains(
                    failure(KafkaTopics.ORDER_PAID, lost.getId().toString()),
                    failure(KafkaTopics.ORDER_NOTIFICATION, lost.getId().toString())));
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
            // 장애 중 만든 producer의 끊긴 연결을 이어 쓰지 않도록 다시 교체한다.
            producerFactory.reset();
        }

        paymentResultUseCase.markPaid(recovered.getId(), recovered.getExpiresAt().minusSeconds(1));

        // 장애 중 커밋된 이벤트는 Outbox에 남아 있다가 브로커 복구 후 발행된다.
        for (String topic : PAYMENT_TOPICS) {
            assertThat(keys(recordsUntil(topic, lost.getId()))).contains(lost.getId().toString());
            assertThat(keys(recordsUntil(topic, recovered.getId()))).contains(recovered.getId().toString());
        }
        awaitOutboxEmpty();
    }

    @Test
    @DisplayName("브로커 장애 중 주문 취소가 커밋되면 Outbox에 남은 취소 이벤트가 복구 후 발행된다")
    void when_broker_is_unavailable_at_cancellation_commit_canceled_event_is_published_after_recovery() {
        Order order = saveOrder();
        paymentResultUseCase.markPaid(order.getId(), order.getExpiresAt().minusSeconds(1));
        // 결제 이벤트가 먼저 모두 발행돼야 장애 중 실패한 전송이 취소 이벤트인지 구분할 수 있다.
        awaitOutboxEmpty();

        producerFactory.reset();
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            orderCancellationService.cancel(order.getCustomerId(), "cancel-" + order.getId(), order.getOrderNumber());

            assertThat(jdbcTemplate.queryForObject(
                    "select status from p_orders where id = ?", String.class, order.getId())).isEqualTo("CANCELED");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(SEND_FAILURES)
                    .contains(failure(KafkaTopics.ORDER_CANCELED, order.getId().toString())));
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
            producerFactory.reset();
        }

        String canceledPayload = recordsUntil(KafkaTopics.ORDER_CANCELED, order.getId()).stream()
                .filter(record -> record.key().equals(order.getId().toString()))
                .findFirst()
                .orElseThrow()
                .value();
        assertThat(jsonMapper.readValue(canceledPayload, OrderCanceledEvent.class).orderId()).isEqualTo(order.getId());
        awaitOutboxEmpty();
    }

    private void awaitOutboxEmpty() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                jdbcTemplate.queryForObject("select count(*) from p_order_event_outbox", Integer.class)).isZero());
    }

    private static String failure(String topic, String key) {
        return topic + ":" + key;
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
