package com.sub9.productservice.category.application.command.port.out;

import com.sub9.productservice.category.domain.event.HashtagCreatedEvent;

public interface HashtagCreatedEventPort {
    void record(HashtagCreatedEvent event);
}
