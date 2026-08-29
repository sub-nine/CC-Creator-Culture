package com.sub9.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("공통 오류 코드")
class CommonErrorCodeTest {

    @Test
    @DisplayName("공통 오류 코드는 정의된 HTTP status와 매핑된다")
    void when_common_error_codes_are_defined_then_map_to_expected_http_statuses() {
        assertThat(List.of(CommonErrorCode.values()))
                .extracting(ErrorCode::code, ErrorCode::status)
                .containsExactly(
                        tuple("COMMON_0001", HttpStatus.INTERNAL_SERVER_ERROR),
                        tuple("COMMON_0002", HttpStatus.BAD_REQUEST),
                        tuple("COMMON_0003", HttpStatus.BAD_REQUEST),
                        tuple("COMMON_0004", HttpStatus.NOT_FOUND),
                        tuple("COMMON_0005", HttpStatus.METHOD_NOT_ALLOWED),
                        tuple("COMMON_0006", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                        tuple("COMMON_0007", HttpStatus.UNAUTHORIZED),
                        tuple("COMMON_0008", HttpStatus.FORBIDDEN));
    }

    private static Tuple tuple(String code, HttpStatus status) {
        return Assertions.tuple(code, status);
    }
}
