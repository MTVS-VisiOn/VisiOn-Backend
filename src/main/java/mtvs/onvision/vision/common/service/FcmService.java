package mtvs.onvision.vision.common.service;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.common.constant.DataMessageType;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static mtvs.onvision.vision.alert.service.AlertService.SEOUL;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {
    private final FirebaseMessaging firebaseMessaging;
    private final UserService userService;

    @Value("${alert.retry.max-attempts}")
    private int maxAttempts;

    @Value("${alert.push.ttl}")
    private Duration pushCommandTtl;
    @Value("${alert.push.signal-ttl}")
    private Duration signalPushTtl;

    private static final long BACKOFF_MILLIS = 200L;

    /** 푸시 제목의 발생 시각. 응답과 같은 KST 기준이다 */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);

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
    public Map<String, NotifyStatus> sendNotification(Long alertId, AlertType type, Instant occurredAt, List<String> fids) {
        Map<String, NotifyStatus> results = new LinkedHashMap<>();
        for (String fid : fids) {
            results.put(fid, sendToDevice(alertId, type, occurredAt, fid));
        }
        return results;
    }

    /** 기기 한 대에 보낸다. 일시 장애면 maxAttempts까지 즉시 재시도한다 */
    // 알림사항
    public NotifyStatus sendToDevice(Long alertId, AlertType type, Instant occurredAt, String fid) {
        Message message = buildMessage(alertId, type, occurredAt, fid);
        return send(fid, message);
    }

    //앱으로 보내는 지시사항
    public void sendToDevice(Long commandId, String content, DataMessageType type, Instant occurredAt, String fid) {
        Message message = buildMessage(commandId, type, content, occurredAt, fid);
        send(fid, message);
    }

    /** 시그널링 방 개설 통지. 저장되는 이벤트가 아니라 id 가 없다 */
    public void sendSignalReady(String type, Instant occurredAt, String fid) {
        send(fid, Message.builder()
                .setFid(fid)
                .putData("type", type)
                .putData("occurredAt", occurredAt.atZone(SEOUL).toLocalDateTime().toString())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(signalPushTtl.toMillis())
                        .build())
                .build());
    }

    private NotifyStatus send(String fid, Message message) {
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
                    return NotifyStatus.UNREGISTERED;
                }
                if (!RETRIABLE.contains(code)) {
                    return NotifyStatus.EXPIRED;
                }
                if (attempt < maxAttempts) {
                    sleep(BACKOFF_MILLIS * attempt);
                }
            }
        }
        return NotifyStatus.FAILED;
    }

    private Message buildMessage(Long alertId, AlertType type, Instant occurredAt, String fid) {
        return Message.builder()
                .setFid(fid)
                .setNotification(Notification.builder()
                        .setTitle(titleOf(type, occurredAt))
                        .setBody(type.getMessage())
                        .build())
                .putData("alertId", alertId.toString())
                .putData("type", type.name())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        // 기기가 오프라인인 건 장애가 아니다. 켜지면 그때라도 배달되게 둔다.
                        // 서버 재시도 만료(alert.retry.expire-minutes)와는 다른 층이다
                        .setTtl(pushCommandTtl.toMillis())
                        .build())
                .build();
    }

    private Message buildMessage(Long commandId, DataMessageType type, String content, Instant occurredAt, String fid) {
        return Message.builder()
                .setFid(fid)
                .putData("alertId", commandId.toString())
                .putData("type", type.name())
                .putData("content", content)
                .putData("occurredAt", occurredAt.atZone(SEOUL).toLocalDateTime().toString())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        // 기기가 오프라인인 건 장애가 아니다. 켜지면 그때라도 배달되게 둔다.
                        // 서버 재시도 만료(alert.retry.expire-minutes)와는 다른 층이다
                        .setTtl(pushCommandTtl.toMillis())
                        .build())
                .build();
    }

    /**
     * 예) "오후 3:12 · 장애물 감지". 늦게 도착해도 언제 일어난 일인지 알 수 있어야 한다.
     * <p>
     * Firebase {@code Message}는 만든 내용을 되읽을 수 없어 package-private으로 열어 직접 검증한다.
     */
    String titleOf(AlertType type, Instant occurredAt) {
        return TIME.format(occurredAt.atZone(SEOUL)) + " · " + type.getLabel();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
