package mtvs.onvision.vision.location.dto;

import mtvs.onvision.vision.location.domain.MovementStatus;

import java.time.Instant;

public record LocationReport(
        Long userId,
        Double latitude,          // 위도
        Double longitude,         // 경도
        Float accuracy,           // 위치 정확도(m), 반경 오차, nullable
        Float speed,              // 속도(m/s), nullable
        MovementStatus status,    //움직이는 상태
        Instant recordedAt        // 측정된 시각
) {
        public static LocationReport from(LocationRequest request, Long userId, MovementStatus status) {
                return new LocationReport(userId, request.latitude(), request.longitude(), request.accuracy(), request.speed(),status, request.recordedAt());
        }
}
