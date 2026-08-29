package com.sub9.common.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공통 성공 응답")
class ApiResponseTest {

    @Test
    @DisplayName("성공 응답은 기본 메시지와 데이터를 반환한다")
    void when_success_is_created_then_returns_default_message_and_data() {
        ApiResponse<String> response = ApiResponse.success("data");

        assertThat(response.getMessage()).isEqualTo("요청 성공");
        assertThat(response.getData()).isEqualTo("data");
    }

    @Test
    @DisplayName("성공 메시지를 직접 지정할 수 있다")
    void when_success_message_is_provided_then_returns_custom_message() {
        ApiResponse<Integer> response = ApiResponse.success("조회 성공", 1);

        assertThat(response.getMessage()).isEqualTo("조회 성공");
        assertThat(response.getData()).isEqualTo(1);
    }
}
