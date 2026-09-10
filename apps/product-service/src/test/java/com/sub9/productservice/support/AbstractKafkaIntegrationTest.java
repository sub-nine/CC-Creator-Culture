package com.sub9.productservice.support;

import com.sub9.common.kafka.topic.KafkaTopics;
import org.springframework.kafka.test.context.EmbeddedKafka;

/** 실제 @KafkaListener/KafkaTemplate을 검증해야 하는 테스트에서 상속해서 사용 */
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaTopics.HASHTAG_CREATED,
                KafkaTopics.PRODUCT_CREATED
        }
)
public abstract class AbstractKafkaIntegrationTest extends AbstractIntegrationTest {
}
