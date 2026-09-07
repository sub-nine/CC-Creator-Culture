package com.sub9.orderservice.payment.presentation.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("모의 결제 요청 검증")
class MockPaymentRequestTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED"})
    @DisplayName("명시적인 성공 또는 실패 결과만 허용한다")
    void when_result_is_supported_validation_succeeds(String result) {
        var request = mapper.readValue("{\"result\":\"" + result + "\"}", MockPaymentRequest.class);
        assertThat(VALIDATORS.getValidator().validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "success", "failed", "PENDING", "0", "SUCCESS "})
    @DisplayName("결제 결과가 없거나 허용값이 아니면 거부한다")
    void when_result_is_invalid_validation_fails(String result) {
        assertThat(VALIDATORS.getValidator().validate(new MockPaymentRequest(result))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"result\":\"SUCCESS\",\"amount\":0}",
            "{\"userId\":\"other-user\",\"result\":\"SUCCESS\"}",
            "{\"result\":\"FAILED\",\"processedAt\":null}",
            "{\"result\":\"SUCCESS\",\"extra\":{}}"
    })
    @DisplayName("전역 설정이 추가 필드를 무시하더라도 결제 요청에서는 거부한다")
    void when_unknown_field_is_present_deserialization_fails(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, MockPaymentRequest.class))
                .isInstanceOf(JacksonException.class)
                .hasRootCauseMessage("결제 요청에는 result만 입력할 수 있습니다.");
    }
}
