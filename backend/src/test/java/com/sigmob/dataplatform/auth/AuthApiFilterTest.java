package com.sigmob.dataplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigmob.dataplatform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthApiFilterTest {

    @Test
    void rejectsProtectedApiWithoutSession() throws Exception {
        var filter = new AuthApiFilter(properties(true), new ObjectMapper().findAndRegisterModules());
        var request = new MockHttpServletRequest("GET", "/api/v1/dashboard/summary");
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTHENTICATION_REQUIRED");
    }

    @Test
    void acceptsProtectedApiWithAuthenticatedSession() throws Exception {
        var filter = new AuthApiFilter(properties(true), new ObjectMapper().findAndRegisterModules());
        var request = new MockHttpServletRequest("GET", "/api/v1/dashboard/summary");
        request.getSession().setAttribute(AuthSession.USER_ATTRIBUTE,
                new AuthModels.AuthUser("ou_test", "on_test", "测试用户", "", "tenant"));
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertThat(called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void bypassesAuthenticationWhenFeatureIsDisabled() throws Exception {
        var filter = new AuthApiFilter(properties(false), new ObjectMapper().findAndRegisterModules());
        var request = new MockHttpServletRequest("GET", "/api/v1/dashboard/summary");
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> called.set(true));

        assertThat(called).isTrue();
    }

    private AppProperties properties(boolean enabled) {
        return new AppProperties(
                null,
                new AppProperties.AuthSettings(
                        enabled,
                        "cli_test",
                        "secret",
                        "http://localhost:5173/api/v1/auth/callback",
                        "http://localhost:5173/"),
                "",
                "http://localhost:5173",
                ZoneId.of("Asia/Shanghai"));
    }
}
