package com.sub9.userservice.user.presentation.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateMyProfileRequest {

    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    private String nickname;

    @Pattern(regexp = "^[0-9-]+$", message = "전화번호는 숫자와 하이픈만 사용할 수 있습니다.")
    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    private String phone;

    @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
    private String address;

    @Size(max = 100, message = "Slack ID는 100자 이하여야 합니다.")
    private String slackId;

    private boolean nicknameProvided;
    private boolean phoneProvided;
    private boolean addressProvided;
    private boolean slackIdProvided;

    public UpdateMyProfileRequest() {
    }

    public UpdateMyProfileRequest(String nickname, String phone, String address, String slackId) {
        setNickname(nickname);
        setPhone(phone);
        setAddress(address);
        setSlackId(slackId);
    }

    public String nickname() {
        return nickname;
    }

    public String phone() {
        return phone;
    }

    public String address() {
        return address;
    }

    public String slackId() {
        return slackId;
    }

    public boolean nicknameProvided() {
        return nicknameProvided;
    }

    public boolean phoneProvided() {
        return phoneProvided;
    }

    public boolean addressProvided() {
        return addressProvided;
    }

    public boolean slackIdProvided() {
        return slackIdProvided;
    }

    public void setNickname(String nickname) {
        nicknameProvided = true;
        this.nickname = normalizeText(nickname);
    }

    public void setPhone(String phone) {
        phoneProvided = true;
        this.phone = phone == null ? null : phone.trim().replace("-", "");
    }

    public void setAddress(String address) {
        addressProvided = true;
        this.address = normalizeText(address);
    }

    public void setSlackId(String slackId) {
        slackIdProvided = true;
        String normalized = normalizeText(slackId);
        this.slackId = normalized == null || normalized.isEmpty() ? null : normalized;
    }

    @AssertTrue(message = "수정할 정보가 하나 이상 필요합니다.")
    public boolean isAnyFieldProvided() {
        return nicknameProvided || phoneProvided || addressProvided || slackIdProvided;
    }

    @AssertTrue(message = "닉네임은 비어 있을 수 없습니다.")
    public boolean isNicknameValid() {
        return !nicknameProvided || hasText(nickname);
    }

    @AssertTrue(message = "전화번호는 비어 있을 수 없습니다.")
    public boolean isPhoneValid() {
        return !phoneProvided || hasText(phone);
    }

    @AssertTrue(message = "주소는 비어 있을 수 없습니다.")
    public boolean isAddressValid() {
        return !addressProvided || hasText(address);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
