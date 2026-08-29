package com.sub9.common.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.CommonErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공통 오류 응답")
class ErrorResponseTest {

    @Test
    @DisplayName("오류가 없으면 빈 목록을 반환한다")
    void when_errors_are_absent_then_returns_empty_list() {
        ErrorResponse response = ErrorResponse.from(CommonErrorCode.BAD_REQUEST);

        assertThat(response.getErrorCode()).isEqualTo("COMMON_0002");
        assertThat(response.getMessage()).isEqualTo("잘못된 요청입니다.");
        assertThat(response.getErrors()).isEmpty();
        assertThatThrownBy(() -> response.getErrors().add(Map.of("name", "필수입니다.")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("응답 오류 목록은 외부 변경의 영향을 받지 않는다")
    void when_source_errors_change_then_response_remains_unchanged() {
        Map<String, String> fieldError = new HashMap<>();
        fieldError.put("name", "필수입니다.");
        List<Map<String, String>> errors = new ArrayList<>();
        errors.add(fieldError);

        ErrorResponse response = ErrorResponse.from(CommonErrorCode.VALIDATION_ERROR, errors);
        fieldError.put("name", "변경된 메시지");
        errors.clear();

        assertThat(response.getErrors()).containsExactly(Map.of("name", "필수입니다."));
        assertThatThrownBy(() -> response.getErrors().getFirst().put("name", "변경"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
