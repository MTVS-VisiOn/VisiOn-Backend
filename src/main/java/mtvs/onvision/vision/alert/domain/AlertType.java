package mtvs.onvision.vision.alert.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AlertType {
    OBSTACLE("장애물 감지", "피보호자 주변에 장애물이 있었어요."),
    LOW_BATTERY("배터리 부족", "피보호자의 기기 배터리가 부족해요."),
    DISCONNECTED("연결 끊김", "피보호자의 기기 연결이 끊겼어요.");

    /** 푸시 제목에 쓴다. 발생 시각과 함께 붙는다 */
    private final String label;

    /**
     * 푸시 본문. 재시도로 늦게 도착할 수 있으므로 과거형으로 쓴다.
     * 현재형이면 보호자가 지금 벌어지는 일로 오인한다.
     */
    private final String message;

}
