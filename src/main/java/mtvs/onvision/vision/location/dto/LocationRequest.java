package mtvs.onvision.vision.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

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
        Instant recordedAt,       // 측정된 시각

        @Size(max = 64)
        @Schema(
                examples = "38b4ef33-9724-42f5-95ca-b7bd021ef889",
                description = """
                        이 GPS 샘플의 식별자. 측정할 때 하나 만들어 두고, 같은 측정값을
                        다시 보낼 때는 **같은 값을 유지한다.**

                        서버는 같은 `sampleId`가 다시 오면 재측정이 아니라 재전송으로 보고
                        저장하지 않는다. 측정 시각만으로는 구분할 수 없기 때문이다 —
                        재전송 때 시각을 전송 시각으로 다시 찍는 구현이 실제로 관측됐다.

                        선택값이다. 없으면 좌표 동일성으로 대신 판정한다"""
        )
        String sampleId
) {

    /** `sampleId`를 붙이기 전 호출부(테스트 픽스처 등)용. */
    public LocationRequest(Double latitude, Double longitude, Float accuracy, Instant recordedAt) {
        this(latitude, longitude, accuracy, recordedAt, null);
    }
}
