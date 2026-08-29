package com.sub9.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("비즈니스 예외")
class BusinessExceptionTest {

    @Test
    @DisplayName("오류 코드의 메시지와 정보를 보존한다")
    void when_business_exception_is_created_then_preserves_error_code_details() {
        BusinessException exception = new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);

        assertThat(exception.getMessage()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
        assertThat(exception.getErrorCode()).isSameAs(CommonErrorCode.RESOURCE_NOT_FOUND);
    }
}
