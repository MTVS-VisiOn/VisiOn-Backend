package mtvs.onvision.vision.command.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.domain.Command;
import mtvs.onvision.vision.command.domain.CommandType;
import mtvs.onvision.vision.command.dto.CommandResponse;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.event.GuardianInstructed;
import mtvs.onvision.vision.command.repository.CommandRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static mtvs.onvision.vision.alert.service.AlertService.SEOUL;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandService {
    private final CommandRepository commandRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void guardianInstruct(InstructionRequest request, CurrentUser currentUser) {
        log.debug("Command instruct requested: guardianId={} role={} tokenType={}",
                currentUser.getId(), currentUser.getRole(), currentUser.getTokenType());
        User ward = userService.getWardFromGuardianId(currentUser.getId());
        Command command = new Command(request.content(), CommandType.GUARDIAN_INSTRUCTION, ward);
        commandRepository.save(command);
        eventPublisher.publishEvent(new GuardianInstructed(command.getId(), command.getContent(), command.getOccurredAt(), ward.getId()));
        //이벤트 발행까지다. 실제 FCM 발송 결과는 리스너 쪽 로그를 봐야 한다
        log.debug("Command instruct saved: commandId={} wardId={} occurredAt={}",
                command.getId(), ward.getId(), command.getOccurredAt());

    }

    @Transactional(readOnly = true)
    public List<CommandResponse> getInstructs(CurrentUser currentUser) {
        Long wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        // 오늘치 → 오늘 KST 00:00부터. 자정이 지나면 목록은 비워진다
        Instant from = LocalDate.now(SEOUL).atStartOfDay(SEOUL).toInstant();
        List<CommandResponse> commands = commandRepository.findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(wardId, from)
                .stream().map(CommandResponse::from).toList();
        log.debug("Command list read: guardianId={} wardId={} from={} count={}",
                currentUser.getId(), wardId, from, commands.size());
        return commands;
    }
}
