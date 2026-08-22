package com.sigmob.dataplatform.auth;

public class FeishuAuthenticationException extends RuntimeException {

    public FeishuAuthenticationException(String message) {
        super(message);
    }

    public FeishuAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
