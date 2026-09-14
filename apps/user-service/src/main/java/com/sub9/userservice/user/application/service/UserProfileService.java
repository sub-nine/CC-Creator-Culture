package com.sub9.userservice.user.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.user.domain.repository.UserRepository;
import com.sub9.userservice.user.presentation.response.MyProfileResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(UUID userId) {
        return userRepository.findActiveById(userId)
                .map(MyProfileResponse::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
