package mtvs.onvision.vision.common.util;

import java.time.ZoneId;

/**
 * 앱 전역의 기준 시간대.
 *
 * 감사 시각(`@CreatedDate`)과 하루 경계 계산이 모두 이 값을 쓴다.
 * JVM 기본 시간대에 맡기면 로컬(KST)과 배포 컨테이너(UTC)가 서로 다른 벽시계를 저장하므로 코드로 고정한다.
 */
public final class AppTime {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private AppTime() {
    }
}
