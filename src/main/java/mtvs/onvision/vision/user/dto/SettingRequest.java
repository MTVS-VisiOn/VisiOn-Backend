package mtvs.onvision.vision.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SettingRequest(
        @NotNull(message = "필수 설정입니다.")
        @Schema(
                examples = {"true", "false"},
                description = "피보호자의 기기 연결 중단 상태가 계속될시 알림 여부 (boolean값으로 설정)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Boolean offlineAlertEnabled,

        @NotNull(message = "필수 설정입니다.")
        @Schema(
                examples = {"true", "false"},
                description = "피보호자가 목적지에 도착할시 알림 여부(boolean값으로 설정)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Boolean arrivalAlertEnabled
) {
}
