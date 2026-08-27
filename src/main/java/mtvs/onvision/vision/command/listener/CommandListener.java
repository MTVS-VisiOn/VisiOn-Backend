package mtvs.onvision.vision.command.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.command.event.GuardianInstructed;
import mtvs.onvision.vision.common.constant.DataMessageType;
import mtvs.onvision.vision.common.service.FcmService;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandListener {
    private final FcmService fcmService;
    private final UserService userService;

    @Async
    @TransactionalEventListener
    public void handleGuardianInstructedEvent(GuardianInstructed event) {
        log.info("Guardian instruction received: commandId={} wardId={}", event.commandId(), event.receiverId());
        // event.toString()에 지시 문구가 들어 있다. prod 기본이 info라(LOG_LEVEL_COMMAND) 평소에는 찍히지 않는다
        log.debug("Guardian instruction detail: {}", event);
        List<String> fids = userService.getFids(event.receiverId());
        if (fids.isEmpty()) {
            log.info("Instruction FCM skipped, no device: commandId={} wardId={}", event.commandId(), event.receiverId());
            return;
        }
        fids.forEach(fid -> logResult(event, fcmService.sendToDevice(
                event.commandId(), event.content(), DataMessageType.GUARDIAN_INSTRUCTION, event.occurredAt(), fid)));
    }

    /**
     * 지시는 재전송하지 않으므로 실패가 남는 곳은 이 로그뿐이다.
     * 시연 검증에서 commandId로 앱 로그와 맞춰봐야 하니 wardId와 함께 남긴다.
     * fid는 기기 식별자라 남기지 않는다 — 실패 원인은 FcmService 쪽 로그에 이미 fid와 함께 찍힌다.
     */
    private void logResult(GuardianInstructed event, NotifyStatus status) {
        if (status == NotifyStatus.SENT) {
            log.info("Instruction FCM sent: commandId={} wardId={}", event.commandId(), event.receiverId());
        } else {
            log.warn("Instruction FCM failed: commandId={} wardId={} status={}", event.commandId(), event.receiverId(), status);
        }
    }
}
