package mtvs.onvision.vision.location.dto;

import java.time.Instant;

/**
 * 이동 판정의 기준점이자, 다음 보고로 넘어가는 판정 상태.
 *
 * 위치 보고는 3초 간격으로 들어오는데, 그 사이 걸어서 움직이는 거리는 GPS 오차 반경보다 작다.
 * 그래서 직전 보고와만 비교하면 어떤 보행 속도로도 반경을 못 넘어 영원히 STATIONARY가 된다
 * (2026-08-24 실기기 검증: accuracy 3m대·3초 간격에서 시속 7.9km 미만이 전부 정지로 나왔다).
 *
 * 판정 기준을 "직전 보고"가 아니라 "마지막으로 판정을 내린 지점"으로 두고, 반경을 벗어날 때까지
 * 그 지점을 들고 간다. 그러면 보고 주기가 3초든 30초든 같은 결과가 나온다.
 *
 * 기준점 좌표와 함께 {@link #vehicleStreak}·{@link #vehicleExitAt}도 들고 간다. 차량 판정은 한 번의
 * 속도 계산으로 확정하지 않고 연속 횟수를 세는데, 그 횟수와 "방금 전까지 차량이었는지"가 보고
 * 사이를 건너 살아남아야 하기 때문이다.
 */
public record MovementAnchor(
        Double latitude,
        Double longitude,
        Float accuracy,           // 기준점 당시의 반경 오차, nullable
        Instant recordedAt,       // 기준점이 찍힌 시각
        Integer vehicleStreak,    // 연속으로 차량 속도가 나온 횟수. 이 필드가 생기기 전 값에는 없어 0으로 채운다
        Instant vehicleExitAt     // 마지막으로 IN_VEHICLE에서 내려온 시각. 차량 확정 중이거나 이력이 없으면 null
) {
    public MovementAnchor {
        vehicleStreak = vehicleStreak == null ? 0 : vehicleStreak;
    }

    public static MovementAnchor of(LocationRequest request) {
        return of(request, 0, null);
    }

    /** 차량 연속 횟수와 하차 시각을 이어받아 기준점만 옮긴다 */
    public static MovementAnchor of(LocationRequest request, int vehicleStreak, Instant vehicleExitAt) {
        return new MovementAnchor(request.latitude(), request.longitude(), request.accuracy(),
                request.recordedAt(), vehicleStreak, vehicleExitAt);
    }

    /** 앵커가 없는 옛 형식을 위한 폴백. 판정 이력이 없으므로 연속 횟수는 0에서 시작한다 */
    public static MovementAnchor of(LocationReport report) {
        return new MovementAnchor(report.latitude(), report.longitude(), report.accuracy(),
                report.recordedAt(), 0, null);
    }
}
