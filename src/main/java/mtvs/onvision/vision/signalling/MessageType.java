package mtvs.onvision.vision.signalling;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum MessageType {
    CONNECTION("connection"),   //웹소켓 연결 시작
    JOIN_ROOM("join_room"),   // 방에 입장
    MATCH("match"),
    OFFER("offer"),     //OFF를 전달 받고 상대 유저에게 sdp 전달
    ANSWER("answer"),    //answer를 전달 받고 방의 다른 유저에게 sdp 전달
    CANDIDATE("candidate"),   //candidate를 전달 받고 방의 다른 유저에게 candidate 전달
    DISCONNECT("disconnect"),   //의도치 않게 웹소켓 연결 종료
    USER_EXIT("user_exit"),   //사용자가 의도적으로 나가기
    OTHER_EXIT("other_exit");   //상대방이 나갔음을 알림

    private final String message;

    // 직렬화: enum → JSON. "OFFER"가 아니라 "offer"로 나감
    @JsonValue
    public String getMessage() {
        return message;
    }

    // 역직렬화: JSON → enum. "offer" 문자열을 OFFER로 매칭
    @JsonCreator
    public static MessageType from(String value) {
        for (MessageType t : values()) {
            if (t.message.equals(value)) return t;
        }
        throw new IllegalArgumentException("알 수 없는 메시지 타입: " + value);
    }
}
