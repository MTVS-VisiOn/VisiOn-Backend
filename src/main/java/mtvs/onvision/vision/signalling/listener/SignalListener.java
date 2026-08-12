package mtvs.onvision.vision.signalling.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.common.constant.DataMessageType;
import mtvs.onvision.vision.common.service.FcmService;
import mtvs.onvision.vision.signalling.event.GuardianEntered;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalListener {
    private final FcmService fcmService;
    private final UserService userService;

    @Async
    @EventListener
    public void handleGuardianEnteredEvent(GuardianEntered event) {
        log.info("Received guardian entered in signal server alert event {}", event);
        List<String> fids = userService.getFids(event.receiverId());
        if (fids.isEmpty()) {
            log.info("No device for ward: wardId={}", event.receiverId());
            return;
        }
        fids.forEach(fid -> fcmService.sendSignalReady(DataMessageType.GUARDIAN_ENTERED.name(), event.occurredAt(), fid));
    }
}
