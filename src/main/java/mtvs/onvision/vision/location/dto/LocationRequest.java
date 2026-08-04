package mtvs.onvision.vision.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record LocationRequest(
        @NotNull(message = "위도는 필수값입니다.")
        @DecimalMin(value = "-90.0", message = "최소 -90 이상어야 합니다.")
        @DecimalMax(value = "90.0", message = "최대 90 이하여야 합니다.")
        @Schema(
                examples = "37.501274",
                description = "위도, -90.0~90.0의 소수점",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Double latitude,          // 위도

        @NotNull(message = "경도는 필수값입니다.")
        @DecimalMin(value = "-180.0", message = "최소 -180 이상어야 합니다.")
        @DecimalMax(value = "180.0", message = "최대 180 이하여야 합니다.")
        @Schema(
                examples = "127.039585",
                description = "경도, -180.0~180.0의 소수점",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Double longitude,         // 경도

        @PositiveOrZero(message = "반경 오차 값은 0이상의 양수입니다.")
        @Schema(
                examples = "12.5",
                description = "반경 오차, 0이상의 양수"
        )
        Float accuracy,           // 위치 정확도(m), 반경 오차, nullable

        @NotNull(message = "측정 시각은 필수값입니다.")
        @Schema(
                examples = "2026-07-26T14:32:10.123Z",
                description = "측정 시간",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant recordedAt        // 측정된 시각
) {
}
