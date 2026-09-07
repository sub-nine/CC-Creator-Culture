package com.sub9.orderservice.coupon.infrastructure.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.port.CouponIssueProcessor;
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
}
