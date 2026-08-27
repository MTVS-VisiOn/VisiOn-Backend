package mtvs.onvision.vision.common.config.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 경로 출발 좌표를 믿을지 판정하는 문턱.
 *
 * <p>상수로 박아 두면 값을 바꿀 때마다 이미지를 다시 빌드해야 한다. 이 값들은 실기기 환경
 * (실내·도심 빌딩숲)에 따라 현장에서 몇 번 돌려 보며 정해야 하는 성질이라 env로 뺐다.
 * 재빌드 없이 {@code docker compose up -d} 만으로 바뀐다.
 *
 * <p>기본값을 함부로 올리면 안 된다 — 2026-08-26 실측에서 accuracy 100m짜리 재전송 좌표로
 * 경로가 만들어져 실제 위치에서 218m 떨어진 곳에서 안내가 시작됐다. 픽스 없음 구간의 최저
 * 관측치가 55.6m이므로 {@code accuracyMaxM}은 그 아래에 두어야 의미가 있다.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "navigation.start")
public class NavigationStartProperties {
    /** 경로 출발점으로 쓸 수 있는 반경 오차 상한(m). 실측 실외 15건이 전부 12m 이하, 픽스 없음이 55~117m다 */
    private final float accuracyMaxM;
    /** 요청에 실려 온 좌표의 수명. 클라이언트가 요청 직전에 측정한 값이라 짧게 잡는다 */
    private final Duration requestMaxAge;
    /** 저장 좌표가 정지 상태일 때의 수명. 정지가 맞다면 나이가 오차를 만들지 않아 가장 길게 줄 수 있다 */
    private final Duration cacheMaxAgeStationary;
    /** 저장 좌표가 이동 중일 때의 수명. 길수록 드리프트가 그대로 출발점 오차가 된다 */
    private final Duration cacheMaxAgeMoving;
    /** 폰 시계가 서버보다 앞설 때 오차로 봐주는 상한. 실측 스큐는 ±1.2초다 */
    private final Duration clockSkewTolerance;
}
