package com.sigmob.dataplatform.auth;

import java.io.Serial;
import java.io.Serializable;

public final class AuthModels {

    private AuthModels() {
    }

    public record AuthStatus(
            boolean authEnabled,
            boolean configured,
            boolean authenticated,
            AuthUser user
    ) {
    }

    public record AuthUser(
            String openId,
            String unionId,
            String name,
            String avatarUrl,
            String tenantKey,
            String email
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
