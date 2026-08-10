package mtvs.onvision.vision.command.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.service.FcmService;
import mtvs.onvision.vision.command.domain.CommandType;
import mtvs.onvision.vision.command.event.GuardianInstructed;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
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
        log.info("Received guardian instructed alert event {}", event);
        List<String> fids = userService.getFids(event.receiverId());
        if (fids.isEmpty()) {
            log.info("No device for ward: wardId={}", event.receiverId());
            return;
        }
        fids.forEach(fid -> fcmService.sendToDevice(event.commandId(), event.content(), CommandType.GUARDIAN_INSTRUCTION, Instant.now(), fid));
    }
}
