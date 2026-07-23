package mtvs.onvision.vision.signalling;

import com.fasterxml.jackson.databind.ObjectMapper;

public class WebSocketUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    //역직렬화 : JSON -> JavaObject
    public static SessionMessage getObject(final String Message) throws Exception {
        return objectMapper.readValue(Message, SessionMessage.class);
    }

    //직렬화
    public static String getString(final SessionMessage message) throws Exception {
        return objectMapper.writeValueAsString(message);
    }
}
