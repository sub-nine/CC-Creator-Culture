package com.sub9.orderservice.order.application.port.output;

public final class StockOperationUncertainException extends RuntimeException {

    public StockOperationUncertainException(String message) {
        super(message);
    }

    public StockOperationUncertainException(String message, Throwable cause) {
        super(message, cause);
    }
}
