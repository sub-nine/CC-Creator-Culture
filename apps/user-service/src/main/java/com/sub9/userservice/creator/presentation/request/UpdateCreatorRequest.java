package com.sub9.userservice.creator.presentation.request;

import com.sub9.userservice.shared.presentation.request.SignupRequestNormalizer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateCreatorRequest {

    @Size(max = 100, message = "상호명은 100자 이하여야 합니다.")
    private String creatorName;

    @Pattern(regexp = "^[0-9]+$", message = "사업자등록번호는 숫자와 하이픈만 사용할 수 있습니다.")
    @Size(max = 20, message = "사업자등록번호는 20자 이하여야 합니다.")
    private String businessRegistrationNumber;

    private boolean creatorNameProvided;
    private boolean businessRegistrationNumberProvided;

    public UpdateCreatorRequest() {
    }

    public UpdateCreatorRequest(String creatorName, String businessRegistrationNumber) {
        setCreatorName(creatorName);
        setBusinessRegistrationNumber(businessRegistrationNumber);
    }

    public String creatorName() {
        return creatorName;
    }

    public String businessRegistrationNumber() {
        return businessRegistrationNumber;
    }

    public boolean creatorNameProvided() {
        return creatorNameProvided;
    }

    public boolean businessRegistrationNumberProvided() {
        return businessRegistrationNumberProvided;
    }

    public void setCreatorName(String creatorName) {
        creatorNameProvided = true;
        this.creatorName = SignupRequestNormalizer.text(creatorName);
    }

    public void setBusinessRegistrationNumber(String businessRegistrationNumber) {
        businessRegistrationNumberProvided = true;
        this.businessRegistrationNumber = SignupRequestNormalizer.number(
                businessRegistrationNumber);
    }

    @AssertTrue(message = "수정할 정보가 하나 이상 필요합니다.")
    public boolean isAnyFieldProvided() {
        return creatorNameProvided || businessRegistrationNumberProvided;
    }

    @AssertTrue(message = "상호명은 비어 있을 수 없습니다.")
    public boolean isCreatorNameValid() {
        return !creatorNameProvided || hasText(creatorName);
    }

    @AssertTrue(message = "사업자등록번호는 비어 있을 수 없습니다.")
    public boolean isBusinessRegistrationNumberValid() {
        return !businessRegistrationNumberProvided || hasText(businessRegistrationNumber);
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
