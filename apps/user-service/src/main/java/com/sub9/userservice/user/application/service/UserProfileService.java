package com.sub9.userservice.user.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.repository.UserRepository;
import com.sub9.userservice.user.presentation.request.UpdateMyProfileRequest;
import com.sub9.userservice.user.presentation.response.MyProfileResponse;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(UUID userId) {
        return userRepository.findActiveById(userId)
                .map(MyProfileResponse::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public MyProfileResponse updateMyProfile(UUID userId, UpdateMyProfileRequest request) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String nickname = request.nicknameProvided() ? request.nickname() : user.getNickname();
        String phone = request.phoneProvided() ? request.phone() : user.getPhone();
        String address = request.addressProvided() ? request.address() : user.getAddress();
        String slackId = request.slackIdProvided() ? request.slackId() : user.getSlackId();

        validateNickname(user, nickname);
        validatePhone(user, phone);
        user.updateProfile(nickname, phone, address, slackId, userId, clock.instant());

        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(UserErrorCode.PROFILE_VALUE_ALREADY_EXISTS);
        }
        return MyProfileResponse.from(user);
    }

    private void validateNickname(User user, String nickname) {
        if (!user.getNickname().equals(nickname)
                && userRepository.existsByNicknameIncludingDeleted(nickname)) {
            throw new BusinessException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
        }
    }

    private void validatePhone(User user, String phone) {
        if (!user.getPhone().equals(phone)
                && userRepository.existsByPhoneIncludingDeleted(phone)) {
            throw new BusinessException(UserErrorCode.PHONE_ALREADY_EXISTS);
        }
    }
}
