package com.sub9.userservice.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원가입 입력 정규화")
class SignupInputNormalizerTest {

    private final SignupInputNormalizer normalizer = new SignupInputNormalizer();

    @Test
    @DisplayName("이메일과 번호를 저장 형식으로 정규화한다")
    void when_email_and_numbers_are_normalized_then_canonical_values_are_returned() {
        assertThat(normalizer.normalizeEmail("  User@Example.COM ")).isEqualTo("user@example.com");
        assertThat(normalizer.normalizePhone(" 010-1234-5678 ")).isEqualTo("01012345678");
        assertThat(normalizer.normalizeBusinessRegistrationNumber(" 123-45-67890 "))
                .isEqualTo("1234567890");
    }

    @Test
    @DisplayName("선택 문자열의 공백 제거 결과가 비어 있으면 null로 변환한다")
    void when_nullable_value_becomes_blank_then_null_is_returned() {
        assertThat(normalizer.normalizeNullable("   ")).isNull();
        assertThat(normalizer.normalizeNullable(null)).isNull();
        assertThat(normalizer.normalizeNullable(" slack-user ")).isEqualTo("slack-user");
    }
}
