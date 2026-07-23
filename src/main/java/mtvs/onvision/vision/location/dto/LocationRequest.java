package mtvs.onvision.vision.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record LocationRequest(
        @NotNull
        @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,          // 위도

        @NotNull
        @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude,         // 경도

        @PositiveOrZero
        Float accuracy,           // 위치 정확도(m), 반경 오차, nullable

        @PositiveOrZero
        Float speed,              // 속도(m/s), nullable

        @NotNull
        Instant recordedAt        // 측정된 시각
) {
}
