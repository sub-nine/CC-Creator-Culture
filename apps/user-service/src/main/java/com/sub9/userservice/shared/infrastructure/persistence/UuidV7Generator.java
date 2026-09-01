package com.sub9.userservice.shared.infrastructure.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidV7Generator {

    public UUID generate() {
        // 직접 비트 구조를 조립하지 않고 RFC 9562를 지원하는 라이브러리에 생성을 맡긴다.
        return UuidCreator.getTimeOrderedEpoch();
    }
}
