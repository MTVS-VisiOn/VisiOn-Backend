package mtvs.onvision.vision.alert.service;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {
    private final FirebaseMessaging firebaseMessaging;
    private final UserService userService;

    //휴대폰에 알림 보내기
    public void sendNotification(Long alertId, AlertType type,
                                 String title, String body, List<String> fids) {
       for (String fid : fids) {
            //메시지 생성
           Message message = Message.builder()
                   .setFid(fid)
                   .setNotification(Notification.builder()
                           .setTitle(title)
                           .setBody(body)
                           .build())
                   .putData("alertId", alertId.toString())
                   .putData("type", type.name())
                   .setAndroidConfig(AndroidConfig.builder()
                           .setPriority(AndroidConfig.Priority.HIGH)
                           .build())
                   .build();
            //전송
            try {
                log.info("Attempting to send Notification (title: {}, body: {}, fid: {})", title, body, fid);
                String response = firebaseMessaging.send(message);
                log.info("Successfully send Notification: {}", response);
            } catch (FirebaseMessagingException e) {
                log.error("Fail to send Notification : {}", e.getMessage());
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    userService.deleteFid(fid);
                    log.error("deleted unregistered fid: {} ", fid);
                }
            }
        }
    }
}
