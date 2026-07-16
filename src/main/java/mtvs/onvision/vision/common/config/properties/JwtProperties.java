package mtvs.onvision.vision.common.config.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private final Validations validations;
    private final Secrets secrets;

    @Getter
    @RequiredArgsConstructor
    public static class Validations {
        private final Long access;
        private final Long refresh;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Secrets {
        private final String appKey;
        private final String originKey;
    }
}