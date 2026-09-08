package com.sub9.userservice.notification.domain.model;

public enum EventType {
    PRODUCT_CREATED,   //상품등록
    PRODUCT_LOW_STOCK, //재고부족
    PRODUCT_SOLD_OUT,  //품절
    PRODUCT_RESTOCKED, //재입고

    ORDER_CREATED,     //주문 생성
    ORDER_CANCELLED,   //주문 취소
    PAYMENT_PAID,      //결제 성공
    PAYMENT_FAILED     //결제 실패
}