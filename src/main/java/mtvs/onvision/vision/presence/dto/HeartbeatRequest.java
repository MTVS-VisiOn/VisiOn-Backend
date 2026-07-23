package mtvs.onvision.vision.presence.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import mtvs.onvision.vision.presence.domain.NetworkType;

import java.time.Instant;

public record HeartbeatRequest(
        @NotNull(message = "연결상태는 필수값입니다.")
        Boolean deviceConnected,

        @NotNull(message = "배터리 상태는 필수값입니다.")
        @Min(value = 0, message = "배터리는 0 이상입니다.")
        @Max(value = 100, message = "배터리는 100 이하입니다.")
        Integer battery,
        @NotNull(message = "네트워크 연결상태는 필수값입니다.")
        NetworkRequest network,
        @NotNull(message = "마지막 신호시간은 필수값입니다.")
        Instant lastHeartbeat,
        @NotNull(message = "마지막 동기화 시간은 필수값입니다.")
        Instant lastSync

) {
    public record NetworkRequest(
        @NotNull(message = "네트워크 타입값은 필수값입니다.")
        NetworkType type,
       @NotNull(message = "네트워크 연결 여부는 필수값입니다.")
       Boolean connected
    ){}
}
