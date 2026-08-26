package mtvs.onvision.vision.navigation.dto;

import java.time.Instant;
import java.util.List;

/**
 * 티맵에 보낼 출발 좌표와 그 출처·품질.
 *
 * <p>좌표만 넘기면 응답에 출처를 실을 수 없어 클라이언트가 안내를 믿을지 판단할 근거가 사라진다.
 * 그래서 판정 결과를 한 덩어리로 들고 다닌다.
 *
 * @param coordinate [위도, 경도]
 * @param source     REQUEST 또는 SERVER_CACHE
 * @param accuracy   그 좌표의 반경 오차(m). 모르면 null
 * @param recordedAt 그 좌표의 측정 시각. 모르면 null
 */
public record StartOrigin(
        List<Double> coordinate,
        StartSource source,
        Float accuracy,
        Instant recordedAt
) {
}
