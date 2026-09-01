package com.sub9.userservice.auth.presentation.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatorSignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        String nickname,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^[0-9-]+$", message = "전화번호는 숫자와 하이픈만 사용할 수 있습니다.")
        @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
        String phone,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @Size(max = 100, message = "Slack ID는 100자 이하여야 합니다.")
        String slackId,

        @NotBlank(message = "상호명은 필수입니다.")
        @Size(max = 100, message = "상호명은 100자 이하여야 합니다.")
        String creatorName,

        @NotBlank(message = "사업자등록번호는 필수입니다.")
        @Pattern(regexp = "^[0-9-]+$", message = "사업자등록번호는 숫자와 하이픈만 사용할 수 있습니다.")
        @Size(max = 20, message = "사업자등록번호는 20자 이하여야 합니다.")
        String businessRegistrationNumber
) {

    public CreatorSignupRequest {
        // 검증 전에 표준 형식으로 바꿔 표기 차이가 검증과 중복 조회를 우회하지 않도록 한다.
        email = SignupRequestNormalizer.email(email);
        nickname = SignupRequestNormalizer.text(nickname);
        phone = SignupRequestNormalizer.number(phone);
        address = SignupRequestNormalizer.text(address);
        slackId = SignupRequestNormalizer.nullableText(slackId);
        creatorName = SignupRequestNormalizer.text(creatorName);
        businessRegistrationNumber = SignupRequestNormalizer.number(businessRegistrationNumber);
    }
}
