package com.sub9.userservice.notification.domain.model;

public enum EventProcessingStatus {
    RECEIVED,    //이벤트 수신함
    PROCESSING,  //이벤트 처리중
    COMPLETED,   //이벤트 처리완료
    FAILED;      //이벤트 처리실패

    public boolean isRetryable() {
        return this == FAILED;
    }
}