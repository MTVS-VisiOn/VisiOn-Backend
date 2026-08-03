package mtvs.onvision.vision.common.config.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "ice")
public class IceProperties {
    private final List<String> stunUrls;
    private final Turn turn;

    @Getter
    @RequiredArgsConstructor
    public static class Turn {
        private final List<String> urls;
        private final String secret;
        private final Long ttl;
    }
}
