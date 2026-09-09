package com.sub9.orderservice.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sub9.common.kafka.event.OrderNotificationEvent;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("주문 알림 발행 트랜잭션 경계")
class OrderNotificationTransactionTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {
    }

    @Test
    @DisplayName("커밋 이후에만 발행하며 전송 실패에도 저장 결과를 유지한다")
    void when_commit_succeeds_send_failure_preserves_saved_result() {
        try (var context = context()) {
            var kafka = context.getBean(KafkaTemplate.class);
            var jdbc = new JdbcTemplate(context.getBean(DriverManagerDataSource.class));
            when(kafka.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("send failed")));

            transaction(context).executeWithoutResult(status -> {
                jdbc.update("insert into notification_test values (1)");
                context.publishEvent(event());
                verify(kafka, never()).send(anyString(), anyString(), anyString());
            });

            verify(kafka).send(anyString(), anyString(), anyString());
            assertThat(jdbc.queryForObject("select count(*) from notification_test", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("후속 저장 실패로 롤백되면 이벤트를 발행하지 않는다")
    void when_transaction_rolls_back_event_is_not_sent() {
        try (var context = context()) {
            var kafka = context.getBean(KafkaTemplate.class);
            var jdbc = new JdbcTemplate(context.getBean(DriverManagerDataSource.class));

            assertThatThrownBy(() -> transaction(context).executeWithoutResult(status -> {
                jdbc.update("insert into notification_test values (1)");
                context.publishEvent(event());
                throw new IllegalStateException("subsequent persistence failed");
            })).isInstanceOf(IllegalStateException.class);

            verify(kafka, never()).send(anyString(), anyString(), anyString());
            assertThat(jdbc.queryForObject("select count(*) from notification_test", Integer.class)).isZero();
        }
    }

    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext();
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        context.register(Config.class);
        context.registerBean(DriverManagerDataSource.class, () -> source);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class));
        context.registerBean(JsonMapper.class, () -> new JsonMapper());
        context.registerBean(OrderNotificationKafkaPublisher.class);
        context.refresh();
        new JdbcTemplate(source).execute("create table notification_test (id integer primary key)");
        return context;
    }

    private TransactionTemplate transaction(AnnotationConfigApplicationContext context) {
        return new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
    }

    private OrderNotificationEvent event() {
        return new OrderNotificationEvent(UUID.randomUUID(), "PAYMENT_PAID", "ORDER_SERVICE", "ORDER",
                UUID.randomUUID(), UUID.randomUUID(), "ORD-TEST", "PAID", null, Instant.now());
    }
}
