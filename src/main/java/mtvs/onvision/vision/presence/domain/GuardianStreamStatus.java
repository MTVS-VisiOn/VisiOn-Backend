package mtvs.onvision.vision.presence.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum GuardianStreamStatus {
    IDLE("idle","초기 상태"),
    LOADING_ICE_SERVERS("loading_ice_servers","/api/ice-servers 호출중"),
    CONNECTING_SIGNALING("connecting_signaling","/signal-raw 웹소켓 연결중"),
    WAITING_FOR_GUARDIAN("waiting_for_guardian","방에 들어갔고 보호자 기다리는 중"),
    NEGOTIATING("negotiating","offer/answer/candidate 교환중"),
    STREAMING("streaming","영상이 흐르는 중"),
    UNKNOWN("unknown","알 수 없음"),
    FAILED("failed","실패");

    private final String value;
    private final String description;

    // 직렬화: enum → JSON. "OFFER"가 아니라 "offer"로 나감
    @JsonValue
    public String getValue() {
        return value;
    }

    // 역직렬화: JSON → enum. "offer" 문자열을 OFFER로 매칭
    @JsonCreator
    public static GuardianStreamStatus from(String value) {
        for (GuardianStreamStatus v : values()) {
            if (v.value.equals(value)) return v;
        }
        return UNKNOWN;
    }
}
