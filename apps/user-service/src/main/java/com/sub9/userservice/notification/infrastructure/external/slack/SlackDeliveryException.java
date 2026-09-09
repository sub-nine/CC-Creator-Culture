package com.sub9.userservice.notification.infrastructure.external.slack;

import lombok.Getter;

@Getter
public class SlackDeliveryException extends RuntimeException {

    private final String code;

    public SlackDeliveryException(String code, String message) {
        super(message);
        this.code = code;
    }

}
