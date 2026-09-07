package com.sub9.orderservice.coupon.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.application.dto.CouponIssueFailureType;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionSystemException;

@DisplayName("쿠폰 발급 실패 분류")
class CouponIssueFailureClassifierTest {

    private final CouponIssueFailureClassifier classifier = new CouponIssueFailureClassifier();

    @Test
    @DisplayName("사용자 쿠폰 중복 제약 위반만 이미 발급된 실패로 분류한다")
    void classifies_duplicate_issue_constraint() {
        RuntimeException exception = integrityViolation("uk_user_coupon_user_coupon");

        assertThat(classifier.classify(exception)).isEqualTo(CouponIssueFailureType.ALREADY_ISSUED);
    }

    @Test
    @DisplayName("다른 데이터 제약 위반은 Redis 선점 해제가 필요한 실패로 분류한다")
    void classifies_other_constraint_as_release_required() {
        RuntimeException exception = integrityViolation("chk_user_coupon_usage");

        assertThat(classifier.classify(exception)).isEqualTo(CouponIssueFailureType.RELEASE_REQUIRED);
    }

    @Test
    @DisplayName("이미 발급된 쿠폰 업무 오류는 이미 발급된 실패로 유지한다")
    void classifies_already_issued_business_error() {
        RuntimeException exception = new BusinessException(CouponErrorCode.ALREADY_ISSUED);

        assertThat(classifier.classify(exception)).isEqualTo(CouponIssueFailureType.ALREADY_ISSUED);
    }

    @Test
    @DisplayName("기간과 품절 같은 롤백된 업무 오류는 선점 해제 대상으로 분류한다")
    void classifies_rolled_back_business_error_as_release_required() {
        RuntimeException exception = new BusinessException(CouponErrorCode.NOT_IN_ISSUE_PERIOD);

        assertThat(classifier.classify(exception)).isEqualTo(CouponIssueFailureType.RELEASE_REQUIRED);
    }

    @Test
    @DisplayName("DB timeout과 트랜잭션 시스템 오류는 결과 불명확으로 분류한다")
    void classifies_uncertain_database_errors() {
        assertThat(classifier.classify(new QueryTimeoutException("DB timeout")))
                .isEqualTo(CouponIssueFailureType.RESULT_UNKNOWN);
        assertThat(classifier.classify(new TransactionSystemException("commit failure")))
                .isEqualTo(CouponIssueFailureType.RESULT_UNKNOWN);
    }

    @Test
    @DisplayName("중첩된 원인에서도 사용자 쿠폰 중복 제약 이름을 찾는다")
    void finds_duplicate_constraint_in_nested_cause() {
        RuntimeException exception = new IllegalStateException(
                "wrapped", new IllegalArgumentException("nested", integrityViolation("uk_user_coupon_user_coupon")));

        assertThat(classifier.classify(exception)).isEqualTo(CouponIssueFailureType.ALREADY_ISSUED);
    }

    private RuntimeException integrityViolation(String constraintName) {
        ConstraintViolationException constraintViolation = mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName()).thenReturn(constraintName);
        return new DataIntegrityViolationException("constraint violation", constraintViolation);
    }
}
