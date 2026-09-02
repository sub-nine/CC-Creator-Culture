package com.sub9.userservice.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.auth.presentation.request.CreatorSignupRequest;
import com.sub9.userservice.auth.presentation.request.CustomerSignupRequest;
import com.sub9.userservice.auth.presentation.response.CreatorSignupResponse;
import com.sub9.userservice.auth.presentation.response.CustomerSignupResponse;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원가입 서비스")
class SignupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T06:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(
                userRepository,
                creatorRepository,
                new UuidV7Generator(),
                passwordEncoder,
                new SignupInputNormalizer(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("CUSTOMER 입력을 정규화하고 비밀번호 해시만 저장한다")
    void when_customer_signs_up_then_normalized_customer_is_saved() {
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");

        CustomerSignupResponse response = signupService.signupCustomer(customerRequest());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(userRepository).flush();
        User user = userCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(user.getSlackId()).isNull();
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(response.userId()).isEqualTo(user.getUserId());
    }

    @Test
    @DisplayName("CREATOR 사용자와 PENDING 창작자를 같은 가입 흐름에서 저장한다")
    void when_creator_signs_up_then_user_and_pending_creator_are_saved() {
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");

        CreatorSignupResponse response = signupService.signupCreator(creatorRequest());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Creator> creatorCaptor = ArgumentCaptor.forClass(Creator.class);
        verify(userRepository).save(userCaptor.capture());
        verify(creatorRepository).save(creatorCaptor.capture());
        verify(creatorRepository).flush();
        User user = userCaptor.getValue();
        Creator creator = creatorCaptor.getValue();
        assertThat(user.getRole()).isEqualTo(UserRole.CREATOR);
        assertThat(creator.getUserId()).isEqualTo(user.getUserId());
        assertThat(creator.getBusinessRegistrationNumber()).isEqualTo("1234567890");
        assertThat(creator.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(response.creatorId()).isEqualTo(creator.getCreatorId());
    }

    @ParameterizedTest(name = "{0} 중복")
    @EnumSource(DuplicateField.class)
    @DisplayName("가입 고유 값이 이미 존재하면 해당 중복 오류를 반환한다")
    void when_signup_unique_value_exists_then_corresponding_duplicate_error_is_returned(
            DuplicateField duplicateField) {
        duplicateField.stub(userRepository, creatorRepository);

        assertThatThrownBy(() -> signupService.signupCreator(creatorRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(duplicateField.errorCode));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("DB UNIQUE 제약 충돌은 일반 가입 정보 중복 오류로 변환한다")
    void when_database_unique_constraint_conflicts_then_conflict_error_is_returned() {
        when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(userRepository).flush();

        assertThatThrownBy(() -> signupService.signupCustomer(customerRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(UserErrorCode.SIGNUP_VALUE_ALREADY_EXISTS));
    }

    private CustomerSignupRequest customerRequest() {
        return new CustomerSignupRequest(
                " User@Example.COM ", "Password123!", " user ", "010-1234-5678",
                " 서울시 예시구 ", "   ");
    }

    private CreatorSignupRequest creatorRequest() {
        return new CreatorSignupRequest(
                " Creator@Example.COM ", "Password123!", " creator ", "010-9876-5432",
                " 서울시 예시구 ", null, " 창작상점 ", "123-45-67890");
    }

    private enum DuplicateField {
        EMAIL(UserErrorCode.EMAIL_ALREADY_EXISTS) {
            @Override
            void stub(UserRepository userRepository, CreatorRepository creatorRepository) {
                when(userRepository.existsByEmailIncludingDeleted("creator@example.com"))
                        .thenReturn(true);
            }
        },
        NICKNAME(UserErrorCode.NICKNAME_ALREADY_EXISTS) {
            @Override
            void stub(UserRepository userRepository, CreatorRepository creatorRepository) {
                when(userRepository.existsByNicknameIncludingDeleted("creator")).thenReturn(true);
            }
        },
        PHONE(UserErrorCode.PHONE_ALREADY_EXISTS) {
            @Override
            void stub(UserRepository userRepository, CreatorRepository creatorRepository) {
                when(userRepository.existsByPhoneIncludingDeleted("01098765432")).thenReturn(true);
            }
        },
        CREATOR_NAME(UserErrorCode.CREATOR_NAME_ALREADY_EXISTS) {
            @Override
            void stub(UserRepository userRepository, CreatorRepository creatorRepository) {
                when(creatorRepository.existsByCreatorNameIncludingDeleted("창작상점"))
                        .thenReturn(true);
            }
        },
        BUSINESS_NUMBER(UserErrorCode.BUSINESS_REGISTRATION_NUMBER_ALREADY_EXISTS) {
            @Override
            void stub(UserRepository userRepository, CreatorRepository creatorRepository) {
                when(creatorRepository.existsByBusinessRegistrationNumberIncludingDeleted(
                        "1234567890")).thenReturn(true);
            }
        };

        private final UserErrorCode errorCode;

        DuplicateField(UserErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        abstract void stub(UserRepository userRepository, CreatorRepository creatorRepository);
    }
}
