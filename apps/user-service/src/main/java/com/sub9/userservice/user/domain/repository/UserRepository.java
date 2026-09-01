package com.sub9.userservice.user.domain.repository;

import com.sub9.userservice.user.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    // 사용자 저장과 조회에 필요한 기능을 선언하는 도메인 인터페이스

    User save(User user);

    Optional<User> findActiveById(UUID userId);

    Optional<User> findActiveByEmail(String email);

    // 탈퇴한 계정의 고유 값도 재사용할 수 없으므로 삭제 여부와 관계없이 검사한다.
    boolean existsByEmailIncludingDeleted(String email);

    boolean existsByNicknameIncludingDeleted(String nickname);

    boolean existsByPhoneIncludingDeleted(String phone);
}
