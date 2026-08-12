package mtvs.onvision.vision.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceRegisterRequest(
        @NotBlank
        String code,
        @NotBlank
        String deviceName,
        @NotBlank
        String deviceSerialTail
) {
}
