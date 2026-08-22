package com.sigmob.dataplatform.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        KafkaSettings kafka,
        AuthSettings auth,
        String importFile,
        String corsAllowedOrigins,
        ZoneId zoneId
) {
    public AppProperties {
        kafka = kafka == null ? new KafkaSettings(false, "oss_data", Duration.ofSeconds(30)) : kafka;
        auth = auth == null ? new AuthSettings(false, "", "", "", "http://localhost:5173/") : auth;
        importFile = importFile == null ? "" : importFile;
        corsAllowedOrigins = corsAllowedOrigins == null ? "http://localhost:5173" : corsAllowedOrigins;
        zoneId = zoneId == null ? ZoneId.of("Asia/Shanghai") : zoneId;
    }

    public record KafkaSettings(boolean enabled, String topic, Duration quietPeriod) {
        public KafkaSettings {
            topic = topic == null || topic.isBlank() ? "oss_data" : topic;
            quietPeriod = quietPeriod == null ? Duration.ofSeconds(30) : quietPeriod;
        }
    }

    public record AuthSettings(
            boolean enabled,
            String clientId,
            String clientSecret,
            String redirectUri,
            String frontendUri
    ) {
        public AuthSettings {
            clientId = normalized(clientId);
            clientSecret = normalized(clientSecret);
            redirectUri = normalized(redirectUri);
            frontendUri = normalized(frontendUri);
            if (frontendUri.isBlank()) {
                frontendUri = "http://localhost:5173/";
            }
        }

        public boolean configured() {
            return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
        }

        private static String normalized(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
