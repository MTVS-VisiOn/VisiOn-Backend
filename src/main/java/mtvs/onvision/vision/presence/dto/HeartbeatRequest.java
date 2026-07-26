package mtvs.onvision.vision.presence.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import mtvs.onvision.vision.presence.domain.NetworkType;

import java.time.Instant;

public record HeartbeatRequest(
        @NotNull(message = "연결상태는 필수값입니다.")
        @Schema(
                examples = {"true", "false"},
                description = "기기연결 상태(Boolean)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Boolean deviceConnected,

        @NotNull(message = "배터리 상태는 필수값입니다.")
        @Min(value = 0, message = "배터리는 0 이상입니다.")
        @Max(value = 100, message = "배터리는 100 이하입니다.")
        @Schema(
                examples = "82",
                description = "배터리양, 양수 정수값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer battery,
        @NotNull(message = "네트워크 연결상태는 필수값입니다.")
        NetworkRequest network,
        @NotNull(message = "마지막 신호시간은 필수값입니다.")
        @Schema(
                examples = "2026-07-26T14:32:10.123Z",
                description = "마지막 신호 시간(Instant 형식)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant lastHeartbeat,
        @NotNull(message = "마지막 동기화 시간은 필수값입니다.")
        @Schema(
                examples = "2026-07-26T14:32:09.123Z",
                description = "마지막 동기화 시간 (Instant 형식)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant lastSync

) {
    public record NetworkRequest(
        @NotNull(message = "네트워크 타입값은 필수값입니다.")
        @Schema(
                examples = {"WIFI", "LTE"},
                description = "네트워크 타입값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        NetworkType type,
       @NotNull(message = "네트워크 연결 여부는 필수값입니다.")
       @Schema(
               examples = {"true", "false"},
               description = "이메일 (이메일 형식 필수, 중복 불가)",
               requiredMode = Schema.RequiredMode.REQUIRED
       )
       Boolean connected
    ){}
}
