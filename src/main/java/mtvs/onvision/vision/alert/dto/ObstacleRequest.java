package mtvs.onvision.vision.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ObstacleRequest(
        @NotNull(message = "감지 시각은 필수값입니다.")
        @Schema(
                examples = "2026-08-05T09:12:33.512Z",
                description = "장애물을 감지한 시각. ISO-8601 UTC(Instant 형식). 기기 시계 기준이며 서버 수신 시각과 별도로 저장된다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant occurredAt,

        @NotNull(message = "위도는 필수값입니다.")
        @Schema(
                examples = "37.4979",
                description = "감지 지점의 위도. 이 좌표로 도로명 주소를 역지오코딩해 함께 저장한다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Double latitude,

        @NotNull(message = "경도는 필수값입니다.")
        @Schema(
                examples = "127.0276",
                description = "감지 지점의 경도",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Double longitude,

        @NotNull(message = "감지 내용은 필수값입니다.")
        @Size(max = 255, message = "감지 내용은 255자 이하입니다.")
        @Schema(
                examples = "전방 2m에 자전거가 세워져 있습니다",
                description = "감지한 장애물에 대한 설명. 알림 상세 화면에 그대로 노출된다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String message,

        @Size(max = 100, message = "조치 내용은 100자 이하입니다.")
        @Schema(
                examples = "위험 음성 재생",
                description = "기기가 이미 취한 조치. 생략 가능"
        )
        String action
) {
}
