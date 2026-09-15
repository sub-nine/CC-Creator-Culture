package com.sub9.common.kafka.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sub9.common.kafka.topic.KafkaTopics;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("사용자 탈퇴 이벤트 계약")
class UserDeletedEventTest {

    private static final UUID EVENT_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-14T08:00:00Z");

    @Test
    @DisplayName("이벤트를 JSON으로 변환하고 동일한 계약으로 복원한다")
    void when_event_is_serialized_json_contract_round_trips() {
        UserDeletedEvent event = new UserDeletedEvent(EVENT_ID, USER_ID, OCCURRED_AT);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        String json = mapper.writeValueAsString(event);

        assertThat(mapper.readTree(json).get("eventId").asText()).isEqualTo(EVENT_ID.toString());
        assertThat(mapper.readTree(json).get("userId").asText()).isEqualTo(USER_ID.toString());
        assertThat(mapper.readTree(json).get("occurredAt").asText())
                .isEqualTo("2026-09-14T08:00:00Z");
        assertThat(mapper.readValue(json, UserDeletedEvent.class)).isEqualTo(event);
    }

    @Test
    @DisplayName("이벤트의 필수 값은 null일 수 없다")
    void when_required_value_is_null_event_creation_is_rejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new UserDeletedEvent(null, USER_ID, OCCURRED_AT));
        assertThatNullPointerException()
                .isThrownBy(() -> new UserDeletedEvent(EVENT_ID, null, OCCURRED_AT));
        assertThatNullPointerException()
                .isThrownBy(() -> new UserDeletedEvent(EVENT_ID, USER_ID, null));
    }

    @Test
    @DisplayName("사용자 탈퇴 토픽은 팀 계약 이름을 사용한다")
    void user_deleted_topic_uses_contract_name() {
        assertThat(KafkaTopics.USER_DELETED).isEqualTo("user.deleted");
    }
}
