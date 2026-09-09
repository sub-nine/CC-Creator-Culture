package com.sub9.userservice.auth.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.repository.UserRepository;
import com.sub9.userservice.auth.presentation.request.CreateManagerRequest;
import com.sub9.userservice.auth.presentation.response.ManagerAccountResponse;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerAccountService {

    private final UserRepository userRepository;
    private final UuidV7Generator uuidV7Generator;
    private final PasswordEncoder passwordEncoder;
    private final SignupInputNormalizer normalizer;
    private final Clock clock;

    @Transactional
    public ManagerAccountResponse createManager(UUID masterId, CreateManagerRequest request) {
        String email = normalizer.normalizeEmail(request.email());
        String nickname = normalizer.trim(request.nickname());
        String phone = normalizer.normalizePhone(request.phone());
        String address = normalizer.trim(request.address());
        String slackId = normalizer.normalizeNullable(request.slackId());

        validateDuplicates(email, nickname, phone);

        User manager = User.createManager(
                uuidV7Generator.generate(),
                email,
                passwordEncoder.encode(request.password()),
                nickname,
                phone,
                address,
                slackId,
                masterId,
                clock.instant());

        try {
            userRepository.save(manager);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(UserErrorCode.SIGNUP_VALUE_ALREADY_EXISTS);
        }
        return ManagerAccountResponse.from(manager);
    }

    private void validateDuplicates(String email, String nickname, String phone) {
        if (userRepository.existsByEmailIncludingDeleted(email)) {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByNicknameIncludingDeleted(nickname)) {
            throw new BusinessException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByPhoneIncludingDeleted(phone)) {
            throw new BusinessException(UserErrorCode.PHONE_ALREADY_EXISTS);
        }
    }
}
