package com.sub9.userservice.notification.application.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {

        super("Notification was not found");
    }
}
