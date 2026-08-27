package com.sigmob.dataplatform.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import com.sigmob.dataplatform.auth.AuthModels;
import com.sigmob.dataplatform.auth.AuthSession;
import com.sigmob.dataplatform.auth.FeishuAuthClient;
import com.sigmob.dataplatform.config.AppProperties;
import com.sigmob.dataplatform.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String AUTHORIZE_URL = "https://accounts.feishu.cn/open-apis/authen/v1/authorize";
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SecureRandom secureRandom = new SecureRandom();
    private final AppProperties properties;
    private final FeishuAuthClient feishuAuthClient;
    private final UserAccountService userAccountService;

    public AuthController(AppProperties properties, FeishuAuthClient feishuAuthClient, UserAccountService userAccountService) {
        this.properties = properties;
        this.feishuAuthClient = feishuAuthClient;
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public AuthModels.AuthStatus me(HttpServletRequest request) {
        return status(AuthSession.currentUser(request.getSession(false)));
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(HttpServletRequest request) {
        AppProperties.AuthSettings settings = properties.auth();
        if (!settings.enabled() || !settings.configured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        String state = randomUrlSafe(32);
        String codeVerifier = randomUrlSafe(64);
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthSession.OAUTH_STATE_ATTRIBUTE, state);
        session.setAttribute(AuthSession.PKCE_VERIFIER_ATTRIBUTE, codeVerifier);

        String location = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", settings.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", settings.redirectUri())
                .queryParam("state", state)
                .queryParam("code_challenge", sha256UrlSafe(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        HttpSession session = request.getSession(false);
        if (error != null) {
            clearOAuthState(session);
            redirectWithError(response, "access_denied");
            return;
        }

        String expectedState = sessionValue(session, AuthSession.OAUTH_STATE_ATTRIBUTE);
        String codeVerifier = sessionValue(session, AuthSession.PKCE_VERIFIER_ATTRIBUTE);
        if (code == null || code.isBlank() || !constantTimeEquals(expectedState, state) || codeVerifier.isBlank()) {
            clearOAuthState(session);
            redirectWithError(response, "invalid_state");
            return;
        }

        try {
            AuthModels.AuthUser user = feishuAuthClient.authenticate(code, codeVerifier);
            request.changeSessionId();
            clearOAuthState(session);

            log.info("飞书登录成功: openId={}, name={}", user.openId(), user.name());

            try {
                userAccountService.createOrUpdate(user);
            } catch (Exception e) {
                log.error("用户账户创建/更新失败，但登录继续: openId={}, error={}", user.openId(), e.getMessage(), e);
            }

            session.setAttribute(AuthSession.USER_ATTRIBUTE, user);
            response.sendRedirect(properties.auth().frontendUri());
        } catch (RuntimeException exception) {
            log.warn("飞书登录回调失败: {}", exception.getMessage());
            clearOAuthState(session);
            redirectWithError(response, "authentication_failed");
        }
    }

    @PostMapping("/logout")
    public AuthModels.AuthStatus logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return status(null);
    }

    private AuthModels.AuthStatus status(AuthModels.AuthUser user) {
        AppProperties.AuthSettings settings = properties.auth();
        return new AuthModels.AuthStatus(
                settings.enabled(),
                settings.configured(),
                user != null,
                user);
    }

    private void redirectWithError(HttpServletResponse response, String error) throws IOException {
        String location = UriComponentsBuilder.fromUriString(properties.auth().frontendUri())
                .queryParam("login_error", error)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(location);
    }

    private void clearOAuthState(HttpSession session) {
        if (session != null) {
            session.removeAttribute(AuthSession.OAUTH_STATE_ATTRIBUTE);
            session.removeAttribute(AuthSession.PKCE_VERIFIER_ATTRIBUTE);
        }
    }

    private String sessionValue(HttpSession session, String attribute) {
        if (session == null) {
            return "";
        }
        Object value = session.getAttribute(attribute);
        return value instanceof String text ? text : "";
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String randomUrlSafe(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sha256UrlSafe(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }
}
