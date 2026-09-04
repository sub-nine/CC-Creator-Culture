package com.sub9.userservice.auth.domain.exception;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;

public class AuthenticationTokenStorageException extends BusinessException {

    public AuthenticationTokenStorageException() {
        super(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
