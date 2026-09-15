package com.sub9.productservice.common.config.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Kafka factory SASL 전달")
class KafkaClientSecurityConfigTest {

  private static final String JAAS =
      "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"u\" password=\"p\";";

  @Mock
  private ObjectProvider<org.springframework.boot.kafka.autoconfigure.KafkaProperties>
      springKafkaProperties;

  @Test
  @DisplayName("SASL 설정이 있으면 프로듀서와 컨슈머 팩토리에 보안 속성을 넣고 부트스트랩은 기존 값을 유지한다")
  void when_spring_kafka_has_sasl_factories_include_security_and_keep_bootstrap() {
    var custom = new KafkaProperties("broker:9096", "latest");
    var spring = new org.springframework.boot.kafka.autoconfigure.KafkaProperties();
    spring.getSecurity().setProtocol(SecurityProtocol.SASL_SSL.name());
    spring.getProperties().put(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512");
    spring.getProperties().put(SaslConfigs.SASL_JAAS_CONFIG, JAAS);
    given(springKafkaProperties.getIfAvailable()).willReturn(spring);

    var producerProps =
        new KafkaProducerConfig(custom, springKafkaProperties).producerFactory().getConfigurationProperties();
    var consumerProps =
        new KafkaConsumerConfig(custom, springKafkaProperties).consumerFactory().getConfigurationProperties();

    assertThat(producerProps)
        .containsEntry("security.protocol", "SASL_SSL")
        .containsEntry(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512")
        .containsEntry(SaslConfigs.SASL_JAAS_CONFIG, JAAS)
        .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker:9096");
    assertThat(consumerProps)
        .containsEntry("security.protocol", "SASL_SSL")
        .containsEntry(SaslConfigs.SASL_MECHANISM, "SCRAM-SHA-512")
        .containsEntry(SaslConfigs.SASL_JAAS_CONFIG, JAAS)
        .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker:9096")
        .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
  }

  @Test
  @DisplayName("SASL 설정이 없으면 개발용 평문 부트스트랩만 유지한다")
  void when_spring_kafka_is_absent_factories_keep_plaintext_dev_settings() {
    var custom = new KafkaProperties("localhost:9092", "earliest");
    given(springKafkaProperties.getIfAvailable()).willReturn(null);

    var producerProps =
        new KafkaProducerConfig(custom, springKafkaProperties).producerFactory().getConfigurationProperties();
    var consumerProps =
        new KafkaConsumerConfig(custom, springKafkaProperties).consumerFactory().getConfigurationProperties();

    assertThat(producerProps)
        .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
        .doesNotContainKeys("security.protocol", SaslConfigs.SASL_MECHANISM);
    assertThat(consumerProps)
        .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
        .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .doesNotContainKeys("security.protocol", SaslConfigs.SASL_MECHANISM);
  }
}
