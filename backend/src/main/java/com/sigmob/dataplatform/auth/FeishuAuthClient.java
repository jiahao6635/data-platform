package com.sigmob.dataplatform.auth;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sigmob.dataplatform.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class FeishuAuthClient {

    private static final String TOKEN_URL = "https://accounts.feishu.cn/oauth/v3/token";
    private static final String USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";
    private static final String CONTACT_USER_URL = "https://open.feishu.cn/open-apis/contact/v3/users/{user_id}";
    private static final int MAX_ERROR_BODY_LENGTH = 2000;
    private static final Logger log = LoggerFactory.getLogger(FeishuAuthClient.class);

    private final RestClient restClient;
    private final AppProperties properties;

    public FeishuAuthClient(RestClient.Builder restClientBuilder, AppProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public AuthModels.AuthUser authenticate(String code, String codeVerifier) {
        AppProperties.AuthSettings settings = properties.auth();
        if (!settings.enabled() || !settings.configured()) {
            throw new FeishuAuthenticationException("飞书登录尚未完成配置");
        }

        try {
            TokenResponse token = exchangeCode(settings, code, codeVerifier);
            UserInfoResponse userInfo = loadUserInfo(token.accessToken());
            UserInfo user = userInfo.data();
            if (user == null || normalized(user.openId()).isBlank()) {
                throw new FeishuAuthenticationException("飞书返回的用户信息不完整");
            }

            String email = loadUserEmail(token.accessToken(), user.openId());

            return new AuthModels.AuthUser(
                    normalized(user.openId()),
                    normalized(user.unionId()),
                    normalized(user.name()),
                    normalized(user.avatarUrl()),
                    normalized(user.tenantKey()),
                    normalized(email));
        } catch (RestClientResponseException exception) {
            throw new FeishuAuthenticationException("飞书登录请求失败，HTTP " + exception.getStatusCode().value(), exception);
        }
    }

    private TokenResponse exchangeCode(
            AppProperties.AuthSettings settings,
            String code,
            String codeVerifier
    ) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", settings.clientId());
        form.add("client_secret", settings.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", settings.redirectUri());
        form.add("code_verifier", codeVerifier);

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException exception) {
            logHttpFailure("换取 user_access_token", TOKEN_URL, exception);
            throw exception;
        }
        if (response == null || response.code() != 0 || normalized(response.accessToken()).isBlank()) {
            String reason = response == null ? "空响应" : normalized(response.errorDescription());
            log.warn(
                    "飞书换取 user_access_token 失败: code={}, error={}, description={}",
                    response == null ? "<empty>" : response.code(),
                    response == null ? "" : normalized(response.error()),
                    reason);
            throw new FeishuAuthenticationException("无法换取飞书用户凭证" + (reason.isBlank() ? "" : ": " + reason));
        }
        return response;
    }

    private UserInfoResponse loadUserInfo(String accessToken) {
        UserInfoResponse response;
        try {
            response = restClient.get()
                    .uri(USER_INFO_URL)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(UserInfoResponse.class);
        } catch (RestClientResponseException exception) {
            logHttpFailure("获取用户信息", USER_INFO_URL, exception);
            throw exception;
        }
        if (response == null || response.code() != 0) {
            String reason = response == null ? "空响应" : normalized(response.msg());
            log.warn(
                    "飞书获取用户信息失败: code={}, message={}",
                    response == null ? "<empty>" : response.code(),
                    reason);
            throw new FeishuAuthenticationException("无法获取飞书用户信息" + (reason.isBlank() ? "" : ": " + reason));
        }
        return response;
    }

    private String loadUserEmail(String accessToken, String openId) {
        try {
            ContactUserResponse response = restClient.get()
                    .uri(CONTACT_USER_URL + "?user_id_type=open_id&fields=email,enterprise_email", openId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(ContactUserResponse.class);
            if (response == null || response.code() != 0 || response.data() == null || response.data().user() == null) {
                log.warn(
                        "飞书获取用户邮箱失败（非登录阻断）: code={}, message={}",
                        response == null ? "<empty>" : response.code(),
                        response == null ? "" : normalized(response.msg()));
                return "";
            }
            ContactUser contactUser = response.data().user();
            String enterpriseEmail = normalized(contactUser.enterpriseEmail());
            return enterpriseEmail.isBlank() ? normalized(contactUser.email()) : enterpriseEmail;
        } catch (RestClientResponseException exception) {
            logHttpFailure("获取用户邮箱（非登录阻断）", CONTACT_USER_URL, exception);
            return "";
        }
    }

    private void logHttpFailure(String phase, String endpoint, RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString(StandardCharsets.UTF_8);
        if (body.isBlank()) {
            body = "<empty>";
        } else if (body.length() > MAX_ERROR_BODY_LENGTH) {
            body = body.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
        }

        log.warn(
                "飞书{} HTTP 请求失败: endpoint={}, status={}, response={}",
                phase,
                endpoint,
                exception.getStatusCode().value(),
                body);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    record TokenResponse(
            int code,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            String error,
            @JsonProperty("error_description") String errorDescription
    ) {
    }

    record UserInfoResponse(int code, String msg, UserInfo data) {
    }

    record UserInfo(
            String name,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("open_id") String openId,
            @JsonProperty("union_id") String unionId,
            @JsonProperty("tenant_key") String tenantKey
    ) {
    }

    record ContactUserResponse(int code, String msg, ContactUserData data) {
    }

    record ContactUserData(ContactUser user) {
    }

    record ContactUser(
            String email,
            @JsonProperty("enterprise_email") String enterpriseEmail
    ) {
    }
}
