package mtvs.onvision.vision.location.dto;

import java.time.Instant;

/**
 * 이동 판정의 기준점.
 *
 * 위치 보고는 3초 간격으로 들어오는데, 그 사이 걸어서 움직이는 거리는 GPS 오차 반경보다 작다.
 * 그래서 직전 보고와만 비교하면 어떤 보행 속도로도 반경을 못 넘어 영원히 STATIONARY가 된다
 * (2026-08-24 실기기 검증: accuracy 3m대·3초 간격에서 시속 7.9km 미만이 전부 정지로 나왔다).
 *
 * 판정 기준을 "직전 보고"가 아니라 "마지막으로 판정을 내린 지점"으로 두고, 반경을 벗어날 때까지
 * 그 지점을 들고 간다. 그러면 보고 주기가 3초든 30초든 같은 결과가 나온다.
 */
public record MovementAnchor(
        Double latitude,
        Double longitude,
        Float accuracy,           // 기준점 당시의 반경 오차, nullable
        Instant recordedAt        // 기준점이 찍힌 시각
) {
    public static MovementAnchor of(LocationRequest request) {
        return new MovementAnchor(request.latitude(), request.longitude(), request.accuracy(), request.recordedAt());
    }

    public static MovementAnchor of(LocationReport report) {
        return new MovementAnchor(report.latitude(), report.longitude(), report.accuracy(), report.recordedAt());
    }
}
