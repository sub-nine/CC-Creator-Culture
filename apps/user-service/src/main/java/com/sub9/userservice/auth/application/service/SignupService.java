package com.sub9.userservice.auth.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.auth.presentation.request.CreatorSignupRequest;
import com.sub9.userservice.auth.presentation.request.CustomerSignupRequest;
import com.sub9.userservice.auth.presentation.response.CreatorSignupResponse;
import com.sub9.userservice.auth.presentation.response.CustomerSignupResponse;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final UuidV7Generator uuidV7Generator;
    private final PasswordEncoder passwordEncoder;
    private final SignupInputNormalizer normalizer;
    private final Clock clock;

    @Transactional
    public CustomerSignupResponse signupCustomer(CustomerSignupRequest request) {
        NormalizedUserInput input = normalizeUserInput(
                request.email(), request.password(), request.nickname(), request.phone(),
                request.address(), request.slackId());
        validateUserDuplicates(input);

        UUID userId = uuidV7Generator.generate();
        User user = createUser(userId, input, UserRole.CUSTOMER, clock.instant());

        try {
            userRepository.save(user);
            // DB UNIQUE 제약까지 트랜잭션 안에서 확인해 동시 가입 충돌을 409로 변환한다.
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(UserErrorCode.SIGNUP_VALUE_ALREADY_EXISTS);
        }
        return CustomerSignupResponse.from(user);
    }

    @Transactional
    public CreatorSignupResponse signupCreator(CreatorSignupRequest request) {
        NormalizedUserInput userInput = normalizeUserInput(
                request.email(), request.password(), request.nickname(), request.phone(),
                request.address(), request.slackId());
        String creatorName = normalizer.trim(request.creatorName());
        String businessRegistrationNumber = normalizer.normalizeBusinessRegistrationNumber(
                request.businessRegistrationNumber());

        validateUserDuplicates(userInput);
        validateCreatorDuplicates(creatorName, businessRegistrationNumber);

        UUID userId = uuidV7Generator.generate();
        Instant now = clock.instant();
        User user = createUser(userId, userInput, UserRole.CREATOR, now);
        Creator creator = Creator.createPending(
                uuidV7Generator.generate(), userId, creatorName, businessRegistrationNumber, now);

        try {
            userRepository.save(user);
            creatorRepository.save(creator);
            // 두 INSERT를 함께 확인하고 하나라도 실패하면 전체 가입을 롤백한다.
            creatorRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(UserErrorCode.SIGNUP_VALUE_ALREADY_EXISTS);
        }
        return CreatorSignupResponse.from(user, creator);
    }

    private NormalizedUserInput normalizeUserInput(String email, String password, String nickname,
            String phone, String address, String slackId) {
        return new NormalizedUserInput(
                normalizer.normalizeEmail(email),
                password,
                normalizer.trim(nickname),
                normalizer.normalizePhone(phone),
                normalizer.trim(address),
                normalizer.normalizeNullable(slackId));
    }

    private void validateUserDuplicates(NormalizedUserInput input) {
        if (userRepository.existsByEmailIncludingDeleted(input.email())) {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByNicknameIncludingDeleted(input.nickname())) {
            throw new BusinessException(UserErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByPhoneIncludingDeleted(input.phone())) {
            throw new BusinessException(UserErrorCode.PHONE_ALREADY_EXISTS);
        }
    }

    private void validateCreatorDuplicates(String creatorName, String businessRegistrationNumber) {
        if (creatorRepository.existsByCreatorNameIncludingDeleted(creatorName)) {
            throw new BusinessException(UserErrorCode.CREATOR_NAME_ALREADY_EXISTS);
        }
        if (creatorRepository.existsByBusinessRegistrationNumberIncludingDeleted(
                businessRegistrationNumber)) {
            throw new BusinessException(
                    UserErrorCode.BUSINESS_REGISTRATION_NUMBER_ALREADY_EXISTS);
        }
    }

    private User createUser(UUID userId, NormalizedUserInput input, UserRole role, Instant now) {
        return User.create(
                userId,
                input.email(),
                passwordEncoder.encode(input.password()),
                input.nickname(),
                input.phone(),
                input.address(),
                input.slackId(),
                role,
                now);
    }

    private record NormalizedUserInput(
            String email,
            String password,
            String nickname,
            String phone,
            String address,
            String slackId
    ) {
    }
}
