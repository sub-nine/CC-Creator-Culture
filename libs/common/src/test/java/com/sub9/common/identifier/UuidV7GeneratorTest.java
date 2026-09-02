package com.sub9.common.identifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UUID v7 생성기")
class UuidV7GeneratorTest {

    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    @DisplayName("RFC 9562 변형의 서로 다른 UUID v7을 생성한다")
    void when_ids_are_generated_they_are_unique_uuid_v7_values() {
        Set<UUID> generatedIds = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            UUID generatedId = generator.generate();
            assertThat(generatedId.version()).isEqualTo(7);
            assertThat(generatedId.variant()).isEqualTo(2);
            generatedIds.add(generatedId);
        }

        assertThat(generatedIds).hasSize(100);
    }
}
