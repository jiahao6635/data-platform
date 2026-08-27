package com.sigmob.dataplatform.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import com.sigmob.dataplatform.auth.AuthModels;
import com.sigmob.dataplatform.auth.AuthSession;
import com.sigmob.dataplatform.auth.FeishuAuthClient;
import com.sigmob.dataplatform.auth.Pkce;
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
    private static final long PKCE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, PendingPkce> pendingPkce = new ConcurrentHashMap<>();
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

        HttpSession session = request.getSession(true);
        // 每次登录生成独立的 state/code_verifier，并按 state 保存。
        // 不能只存在当前 Session 里：重复点击、多标签、Chrome 双发 GET 都会覆盖 verifier，
        // 用户完成的是旧授权页，回调就会被飞书以 20049 PKCE 失败拒绝。
        String state = Pkce.randomUrlSafe(secureRandom, Pkce.RANDOM_BYTE_LENGTH);
        String codeVerifier = Pkce.randomUrlSafe(secureRandom, Pkce.RANDOM_BYTE_LENGTH);
        rememberPkce(state, codeVerifier);
        session.setAttribute(AuthSession.OAUTH_STATE_ATTRIBUTE, state);
        session.setAttribute(AuthSession.PKCE_VERIFIER_ATTRIBUTE, codeVerifier);

        String location = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", settings.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", settings.redirectUri())
                .queryParam("scope", "contact:contact.base:readonly")
                .queryParam("state", state)
                .queryParam("code_challenge", Pkce.s256Challenge(codeVerifier))
                .queryParam("code_challenge_method", "S256")
                .encode()
                .build()
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

        String codeVerifier = consumePkce(state);
        if (codeVerifier.isBlank() && session != null && constantTimeEquals(sessionValue(session, AuthSession.OAUTH_STATE_ATTRIBUTE), state)) {
            codeVerifier = sessionValue(session, AuthSession.PKCE_VERIFIER_ATTRIBUTE);
        }
        if (code == null || code.isBlank() || codeVerifier.isBlank()) {
            clearOAuthState(session);
            redirectWithError(response, "invalid_state");
            return;
        }

        try {
            AuthModels.AuthUser user = feishuAuthClient.authenticate(code, codeVerifier);
            if (session == null) {
                session = request.getSession(true);
            } else {
                request.changeSessionId();
            }
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

    private void rememberPkce(String state, String codeVerifier) {
        long now = System.currentTimeMillis();
        pendingPkce.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        pendingPkce.put(state, new PendingPkce(codeVerifier, now + PKCE_TTL_MILLIS));
    }

    private String consumePkce(String state) {
        if (state == null || state.isBlank()) {
            return "";
        }
        PendingPkce pending = pendingPkce.remove(state);
        if (pending == null || pending.expiresAtMillis() <= System.currentTimeMillis()) {
            return "";
        }
        return pending.codeVerifier();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private record PendingPkce(String codeVerifier, long expiresAtMillis) {
    }
}
