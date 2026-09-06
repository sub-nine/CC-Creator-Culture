package com.sub9.orderservice.coupon.infrastructure.persistence;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.coupon.application.dto.CouponIssueFailureType;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionSystemException;

@Component
// 쿠폰 DB 발급 예외를 중복 발급, Redis 선점 해제 필요, 결과 불명확으로 분류한다.
public class CouponIssueFailureClassifier {

    static final String DUPLICATE_ISSUE_CONSTRAINT = "uk_user_coupon_user_coupon";

    public CouponIssueFailureType classify(RuntimeException exception) {
        if (isAlreadyIssuedBusinessException(exception) || hasDuplicateIssueConstraint(exception)) {
            return CouponIssueFailureType.ALREADY_ISSUED;
        }
        if (hasResultUnknownCause(exception)) {
            return CouponIssueFailureType.RESULT_UNKNOWN;
        }
        return CouponIssueFailureType.RELEASE_REQUIRED;
    }

    private boolean isAlreadyIssuedBusinessException(RuntimeException exception) {
        return exception instanceof BusinessException businessException
                && businessException.getErrorCode() == CouponErrorCode.ALREADY_ISSUED;
    }

    private boolean hasDuplicateIssueConstraint(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && DUPLICATE_ISSUE_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasResultUnknownCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TransactionSystemException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof QueryTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
