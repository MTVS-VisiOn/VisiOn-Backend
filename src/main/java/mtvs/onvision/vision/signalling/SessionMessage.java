package mtvs.onvision.vision.signalling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)   //역직렬화시 클래스의 없는 필드가 JSON에 들어와도 무시
public class SessionMessage {
    private String sender;
    private MessageType type;
    private String receiver;
    private Long roomId;
    private Object candidate;   //상태
    private Object sdp;  //sdp 정보
}
