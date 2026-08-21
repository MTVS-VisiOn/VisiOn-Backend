package mtvs.onvision.vision.user.dto;

public record PairingDeviceResponse(
        String accessToken,
        String vlmBaseUrl,
        String vlmToken
) {
}
