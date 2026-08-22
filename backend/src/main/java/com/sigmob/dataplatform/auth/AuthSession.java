package com.sigmob.dataplatform.auth;

import jakarta.servlet.http.HttpSession;

public final class AuthSession {

    public static final String USER_ATTRIBUTE = AuthSession.class.getName() + ".user";
    public static final String OAUTH_STATE_ATTRIBUTE = AuthSession.class.getName() + ".oauthState";
    public static final String PKCE_VERIFIER_ATTRIBUTE = AuthSession.class.getName() + ".pkceVerifier";

    private AuthSession() {
    }

    public static AuthModels.AuthUser currentUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(USER_ATTRIBUTE);
        return value instanceof AuthModels.AuthUser user ? user : null;
    }
}
