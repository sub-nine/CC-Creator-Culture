package com.sub9.userservice.notification.domain.service;

import com.sub9.userservice.notification.domain.model.NotificationContext;

public class NotificationMessageFactory {

    public Message create(NotificationContext context) {
        String productName = fallback(context.productName(), "상품");
        String orderNumber = fallback(context.orderNumber(), "주문");

        return switch (context.eventType()) {
            case PRODUCT_CREATED -> new Message("새 상품 등록", productName + " 상품이 새로 등록되었습니다.");
            case PRODUCT_LOW_STOCK -> new Message(
                    "재고 부족",
                    productName + " 재고가 부족합니다. 현재 재고: " + fallback(context.currentStock(), 0)
            );
            case PRODUCT_SOLD_OUT -> new Message("상품 품절", productName + " 상품이 품절되었습니다.");
            case PRODUCT_RESTOCKED -> new Message("상품 재입고", productName + " 상품이 재입고되었습니다.");

            case ORDER_CREATED -> new Message("신규 주문", orderNumber + " 주문이 생성되었습니다.");
            case ORDER_CANCELLED -> new Message("주문 취소", orderNumber + " 주문이 취소되었습니다.");
            case PAYMENT_PAID -> new Message("결제 완료", orderNumber + " 결제가 완료되었습니다.");
            case PAYMENT_FAILED -> new Message("결제 실패", orderNumber + " 결제가 만료되었거나 최종 실패했습니다.");

        };
    }

    private String fallback(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int fallback(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    /** 생성된 알림 문구를 전달하는 불변 값 객체입니다. */
    public record Message(String title, String content) {
    }
}
