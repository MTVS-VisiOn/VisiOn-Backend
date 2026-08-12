package mtvs.onvision.vision.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceRegisterRequest(
        @NotBlank(message = "등록 코드는 필수값입니다.")
        String code,
        @NotBlank(message = "기기 이름은 필수값입니다.")
        String deviceName,
        @NotBlank(message = "기기 코드는 필수값입니다.")
        String deviceSerialTail
) {
}
