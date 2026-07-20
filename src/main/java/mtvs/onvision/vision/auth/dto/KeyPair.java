package mtvs.onvision.vision.auth.dto;

public record KeyPair(
        String accessToken,
        String refreshToken
) {
}
