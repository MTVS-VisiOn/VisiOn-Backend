package mtvs.onvision.vision.location.dto;

import mtvs.onvision.vision.location.domain.MovementStatus;

import java.time.Instant;

public record LocationReport(
        Long userId,
        Double latitude,          // 위도
        Double longitude,         // 경도
        Float accuracy,           // 위치 정확도(m), 반경 오차, nullable
        MovementStatus status,    //움직이는 상태
        Instant recordedAt,       // 측정된 시각
        MovementAnchor anchor,    // 이동 판정 기준점. 이 필드가 생기기 전 저장된 값에는 없어 null일 수 있다
        String sampleId           // 이 좌표를 만든 GPS 샘플의 식별자. 재전송 판별에 쓴다. nullable
) {

        /** `sampleId`가 생기기 전 호출부용. */
        public LocationReport(Long userId, Double latitude, Double longitude, Float accuracy,
                              MovementStatus status, Instant recordedAt, MovementAnchor anchor) {
                this(userId, latitude, longitude, accuracy, status, recordedAt, anchor, null);
        }
        /** 판정을 거치지 않는 호출부(테스트 픽스처 등)용. 앵커 없이 만든다 */
        public LocationReport(Long userId, Double latitude, Double longitude, Float accuracy,
                              MovementStatus status, Instant recordedAt) {
                this(userId, latitude, longitude, accuracy, status, recordedAt, null, null);
        }

        public static LocationReport from(LocationRequest request, Long userId, MovementStatus status, MovementAnchor anchor) {
                return new LocationReport(userId, request.latitude(), request.longitude(), request.accuracy(), status, request.recordedAt(), anchor, request.sampleId());
        }
}
