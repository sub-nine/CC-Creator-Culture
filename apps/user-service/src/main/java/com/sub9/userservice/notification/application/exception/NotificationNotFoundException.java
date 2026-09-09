package com.sub9.userservice.notification.application.exception;

import com.sub9.common.exception.BusinessException;

public class NotificationNotFoundException extends
        BusinessException {

    public NotificationNotFoundException() {
        super(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }
}