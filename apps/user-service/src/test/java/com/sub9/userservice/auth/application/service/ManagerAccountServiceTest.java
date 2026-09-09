package com.sub9.userservice.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.auth.presentation.request.CreateManagerRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("MANAGER 계정 생성 서비스")
class ManagerAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T01:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private ManagerAccountService managerAccountService;

    @BeforeEach
    void setUp() {
        managerAccountService = new ManagerAccountService(
                userRepository,
                new UuidV7Generator(),
                passwordEncoder,
                new SignupInputNormalizer(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("MASTER가 입력한 계정 정보로 MANAGER를 생성한다")
    void when_master_creates_account_manager_is_saved() {
        UUID masterId = new UuidV7Generator().generate();
        when(passwordEncoder.encode("Password123!")).thenReturn("bcrypt-hash");

        var response = managerAccountService.createManager(masterId, request());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        verify(userRepository).flush();
        User manager = captor.getValue();
        assertThat(manager.getEmail()).isEqualTo("manager@example.com");
        assertThat(manager.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(manager.getPhone()).isEqualTo("01012345678");
        assertThat(manager.getRole()).isEqualTo(UserRole.MANAGER);
        assertThat(manager.getCreatedBy()).isEqualTo(masterId);
        assertThat(manager.getUpdatedBy()).isEqualTo(masterId);
        assertThat(response.userId()).isEqualTo(manager.getId());
        assertThat(response.role()).isEqualTo(UserRole.MANAGER);
    }

    @Test
    @DisplayName("이메일이 이미 존재하면 MANAGER를 저장하지 않는다")
    void when_email_already_exists_manager_is_not_saved() {
        when(userRepository.existsByEmailIncludingDeleted("manager@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> managerAccountService.createManager(
                new UuidV7Generator().generate(), request()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS));
        verify(userRepository, never()).save(any());
    }

    private CreateManagerRequest request() {
        return new CreateManagerRequest(
                " Manager@Example.COM ", "Password123!", " manager ", "010-1234-5678",
                " 서울시 예시구 ", "   ");
    }
}
