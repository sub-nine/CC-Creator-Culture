package com.sub9.orderservice.coupon.infrastructure.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.CouponIssueFailureType;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.exception.CouponReservationReleaseRequiredException;
import com.sub9.orderservice.coupon.application.port.CouponIssueProcessor;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.infrastructure.persistence.CouponIssueFailureClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("동기 쿠폰 발급 전달자")
class DirectDispatcherTest {
    @Mock private CouponIssueProcessor processor;
    @Mock private CouponIssueFailureClassifier failureClassifier;
    @InjectMocks private DirectDispatcher dispatcher;
    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    @DisplayName("발급 Processor를 즉시 호출하고 완료 결과를 반환한다")
    void when_dispatching_processor_result_is_returned_as_completed() {
        var reservation = new CouponReservation(
                generator.generate(), generator.generate(), generator.generate());
        var userCouponId = generator.generate();
        given(processor.process(reservation)).willReturn(userCouponId);
        assertThat(dispatcher.dispatch(reservation))
                .isEqualTo(new IssueDispatchResult.Completed(userCouponId));
        verify(processor).process(reservation);
    }

    @Test
    @DisplayName("이미 발급된 실패는 쿠폰 중복 발급 오류로 변환한다")
    void when_failure_is_already_issued_duplicate_error_is_thrown() {
        CouponReservation reservation = reservation();
        RuntimeException failure = new RuntimeException("duplicate");
        given(processor.process(reservation)).willThrow(failure);
        given(failureClassifier.classify(failure)).willReturn(CouponIssueFailureType.ALREADY_ISSUED);

        assertThatThrownBy(() -> dispatcher.dispatch(reservation))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponErrorCode.ALREADY_ISSUED));
    }

    @Test
    @DisplayName("롤백이 확정된 실패는 Redis 선점 해제가 필요한 예외로 변환한다")
    void when_failure_requires_release_release_exception_is_thrown() {
        CouponReservation reservation = reservation();
        RuntimeException failure = new RuntimeException("rolled back");
        given(processor.process(reservation)).willThrow(failure);
        given(failureClassifier.classify(failure)).willReturn(CouponIssueFailureType.RELEASE_REQUIRED);

        assertThatThrownBy(() -> dispatcher.dispatch(reservation))
                .isInstanceOfSatisfying(CouponReservationReleaseRequiredException.class,
                        exception -> assertThat(exception.getCause()).isSameAs(failure));
    }

    @Test
    @DisplayName("결과가 불명확한 실패는 보상 표시 없이 원래 예외를 전달한다")
    void when_result_is_unknown_original_exception_is_thrown() {
        CouponReservation reservation = reservation();
        RuntimeException failure = new RuntimeException("unknown result");
        given(processor.process(reservation)).willThrow(failure);
        given(failureClassifier.classify(failure)).willReturn(CouponIssueFailureType.RESULT_UNKNOWN);

        assertThatThrownBy(() -> dispatcher.dispatch(reservation)).isSameAs(failure);
    }

    private CouponReservation reservation() {
        return new CouponReservation(
                generator.generate(), generator.generate(), generator.generate());
    }
}
