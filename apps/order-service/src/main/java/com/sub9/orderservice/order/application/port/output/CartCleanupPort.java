package com.sub9.orderservice.order.application.port.output;

public interface CartCleanupPort {

    void cleanup(CartCleanupCommand command);
}
