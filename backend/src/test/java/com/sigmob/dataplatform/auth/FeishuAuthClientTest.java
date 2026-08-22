package com.sigmob.dataplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.ZoneId;

import com.sigmob.dataplatform.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FeishuAuthClientTest {

    @Test
    void exchangesCodeAndLoadsCurrentUserWithoutExposingToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new FeishuAuthClient(builder, properties());

        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":0,"access_token":"user-token","expires_in":7200}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"code":0,"msg":"success","data":{"name":"张三","avatar_url":"https://example/avatar.png",\
                        "open_id":"ou_test","union_id":"on_test","tenant_key":"tenant_test"}}
                        """, MediaType.APPLICATION_JSON));

        AuthModels.AuthUser user = client.authenticate("oauth-code", "pkce-verifier");

        assertThat(user.name()).isEqualTo("张三");
        assertThat(user.openId()).isEqualTo("ou_test");
        assertThat(user.tenantKey()).isEqualTo("tenant_test");
        server.verify();
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
