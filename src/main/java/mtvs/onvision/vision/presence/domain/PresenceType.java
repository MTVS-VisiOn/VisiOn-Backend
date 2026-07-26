package mtvs.onvision.vision.presence.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum  PresenceType {
    NORMAL("정상"),
    DELAY_SYNC("동기화 지연"),
    NOT_NETWORK("네트워크 중단"),
    NOT_FOUND("연결 없음");

    private final String description;
}
