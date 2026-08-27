package com.sigmob.dataplatform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZoneId;

import com.sigmob.dataplatform.auth.AuthModels;
import com.sigmob.dataplatform.auth.AuthSession;
import com.sigmob.dataplatform.auth.FeishuAuthClient;
import com.sigmob.dataplatform.auth.Pkce;
import com.sigmob.dataplatform.config.AppProperties;
import com.sigmob.dataplatform.service.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

class AuthControllerTest {

    @Test
    void createsPkceLoginAndStoresAuthenticatedUserInSession() throws Exception {
        FeishuAuthClient client = mock(FeishuAuthClient.class);
        UserAccountService userAccountService = mock(UserAccountService.class);
        var controller = new AuthController(properties(), client, userAccountService);
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/login");

        var login = controller.login(request);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        UriComponents authorize = UriComponentsBuilder.fromUri(login.getHeaders().getLocation()).build();
        String location = login.getHeaders().getLocation().toString();
        assertThat(location)
                .startsWith("https://accounts.feishu.cn/open-apis/authen/v1/authorize?")
                .contains("client_id=cli_test")
                .contains("response_type=code")
                .contains("code_challenge_method=S256");
        assertThat(authorize.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo("http://localhost:5173/api/v1/auth/callback");

        String state = (String) request.getSession().getAttribute(AuthSession.OAUTH_STATE_ATTRIBUTE);
        String verifier = (String) request.getSession().getAttribute(AuthSession.PKCE_VERIFIER_ATTRIBUTE);
        assertThat(state).isNotBlank();
        assertThat(verifier).hasSizeGreaterThanOrEqualTo(43);
        assertThat(authorize.getQueryParams().getFirst("code_challenge")).isEqualTo(Pkce.s256Challenge(verifier));

        var user = new AuthModels.AuthUser("ou_test", "on_test", "张三", "", "tenant", "");
        when(client.authenticate("oauth-code", verifier)).thenReturn(user);
        var response = new MockHttpServletResponse();

        controller.callback("oauth-code", state, null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
        assertThat(controller.me(request).authenticated()).isTrue();
        assertThat(controller.me(request).user()).isEqualTo(user);
        assertThat(request.getSession().getAttribute(AuthSession.OAUTH_STATE_ATTRIBUTE)).isNull();
    }

    @Test
    void callbackUsesVerifierBoundToAuthorizeStateAfterRepeatedLogin() throws Exception {
        FeishuAuthClient client = mock(FeishuAuthClient.class);
        UserAccountService userAccountService = mock(UserAccountService.class);
        var controller = new AuthController(properties(), client, userAccountService);
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/login");

        var firstLogin = controller.login(request);
        String firstState = UriComponentsBuilder.fromUri(firstLogin.getHeaders().getLocation()).build()
                .getQueryParams()
                .getFirst("state");
        String firstVerifier = (String) request.getSession().getAttribute(AuthSession.PKCE_VERIFIER_ATTRIBUTE);

        controller.login(request);
        String secondVerifier = (String) request.getSession().getAttribute(AuthSession.PKCE_VERIFIER_ATTRIBUTE);
        assertThat(secondVerifier).isNotEqualTo(firstVerifier);

        var user = new AuthModels.AuthUser("ou_test", "on_test", "张三", "", "tenant", "");
        when(client.authenticate("oauth-code", firstVerifier)).thenReturn(user);
        var response = new MockHttpServletResponse();

        controller.callback("oauth-code", firstState, null, request, response);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
        assertThat(controller.me(request).authenticated()).isTrue();
    }

    private AppProperties properties() {
        return new AppProperties(
                null,
                new AppProperties.AuthSettings(
                        true,
                        "cli_test",
                        "secret",
                        "http://localhost:5173/api/v1/auth/callback",
                        "http://localhost:5173/"),
                "",
                "http://localhost:5173",
                ZoneId.of("Asia/Shanghai"));
    }
}
