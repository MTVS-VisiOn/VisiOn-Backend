package mtvs.onvision.vision.alert.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AlertType {
    OBSTACLE("피보호자에게 장애물이 다가왔어요. 알림을 확인해주세요."),
    LOW_BATTERY("피보호자의 기기 배터리가 부족해요."),
    DISCONNECTED("피보호자의 기기 연결이 끊겼어요.");

    private final String message;

}
