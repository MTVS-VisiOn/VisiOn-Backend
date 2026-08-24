package mtvs.onvision.vision.common.config.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "vlm")
public class VlmProperties {
    private final String baseUrl;
    private final String token;
}
