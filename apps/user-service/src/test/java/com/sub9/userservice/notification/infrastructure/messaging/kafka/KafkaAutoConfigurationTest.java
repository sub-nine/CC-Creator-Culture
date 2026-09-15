package com.sub9.userservice.notification.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

@DisplayName("User Kafka 자동 구성")
class KafkaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=broker:9096",
                    "spring.kafka.security.protocol=SASL_SSL",
                    "spring.kafka.properties[sasl.mechanism]=SCRAM-SHA-512",
                    "spring.kafka.properties[sasl.jaas.config]=org.apache.kafka.common.security.scram.ScramLoginModule required username=\"u\" password=\"p\";");

    @Test
    @DisplayName("starter-kafka가 있으면 컨슈머 팩토리와 리스너 팩토리를 만들고 SASL 설정을 전달한다")
    void when_starter_kafka_is_present_auto_config_creates_sasl_consumer_factory() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ConsumerFactory.class);
            assertThat(context).hasSingleBean(ConcurrentKafkaListenerContainerFactory.class);

            DefaultKafkaConsumerFactory<?, ?> consumerFactory =
                    context.getBean(DefaultKafkaConsumerFactory.class);
            assertThat(consumerFactory.getConfigurationProperties())
                    .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("broker:9096"))
                    .containsEntry("security.protocol", "SASL_SSL")
                    .containsEntry(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512")
                    .containsEntry(
                            SaslConfigs.SASL_JAAS_CONFIG,
                            "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"u\" password=\"p\";");
        });
    }
}
