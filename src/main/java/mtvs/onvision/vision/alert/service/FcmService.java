package mtvs.onvision.vision.alert.service;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {
    private final FirebaseMessaging firebaseMessaging;
    private final UserService userService;

    @Value("${alert.retry.max-attempts}")
    private int maxAttempts;

    @Value("${alert.retry.expire-minutes}")
    private long expireMinutes;

    private static final long BACKOFF_MILLIS = 200L;

    /** FCM 쪽 일시 장애. 다시 보내면 성공할 수 있다 */
    private static final Set<MessagingErrorCode> RETRIABLE = EnumSet.of(
            MessagingErrorCode.UNAVAILABLE,
            MessagingErrorCode.INTERNAL,
            MessagingErrorCode.QUOTA_EXCEEDED
    );

    /**
     * 기기별 발송 결과를 돌려준다.
     * SENT는 성공, FAILED는 재전송 대상, EXPIRED는 다시 보내도 같은 실패다.
     */
    public Map<String, NotifyStatus> sendNotification(Long alertId, AlertType type, List<String> fids) {
        Map<String, NotifyStatus> results = new LinkedHashMap<>();
        for (String fid : fids) {
            results.put(fid, sendToDevice(alertId, type, fid));
        }
        return results;
    }

    /** 기기 한 대에 보낸다. 일시 장애면 maxAttempts까지 즉시 재시도한다 */
    public NotifyStatus sendToDevice(Long alertId, AlertType type, String fid) {
        Message message = buildMessage(alertId, type, fid);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String response = firebaseMessaging.send(message);
                log.info("Successfully send Notification: {}", response);
                return NotifyStatus.SENT;
            } catch (FirebaseMessagingException e) {
                MessagingErrorCode code = e.getMessagingErrorCode();
                log.error("Fail to send Notification (fid={}, attempt={}/{}, code={}): {}",
                        fid, attempt, maxAttempts, code, e.getMessage());

                if (code == MessagingErrorCode.UNREGISTERED) {
                    userService.deleteFid(fid);
                    log.error("deleted unregistered fid: {}", fid);
                    return NotifyStatus.UNREGISTERED;   // 만료가 아닌 연결된 기기가 사라진 상태
                }
                if (!RETRIABLE.contains(code)) {
                    return NotifyStatus.EXPIRED;   // 요청 자체가 잘못됐다. 재전송해도 같다
                }
                if (attempt < maxAttempts) {
                    sleep(BACKOFF_MILLIS * attempt);
                }
            }
        }
        return NotifyStatus.FAILED;   // 즉시 재시도로 못 넘겼다. 스케줄러가 다시 본다
    }

    private Message buildMessage(Long alertId, AlertType type, String fid) {
        return Message.builder()
                .setFid(fid)
                .setNotification(Notification.builder()
                        .setTitle("알림")
                        .setBody(type.getMessage())
                        .build())
                .putData("alertId", alertId.toString())
                .putData("type", type.name())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        // 기기가 꺼져 있다가 나중에 켜졌을 때 FCM이 쌓아둔 알림을 배달하는 것을 막는다
                        .setTtl(Duration.ofMinutes(expireMinutes).toMillis())
                        .build())
                .build();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
