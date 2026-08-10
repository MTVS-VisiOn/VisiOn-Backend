package mtvs.onvision.vision.command.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.domain.Command;
import mtvs.onvision.vision.command.domain.CommandType;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.event.GuardianInstructed;
import mtvs.onvision.vision.command.repository.CommandRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommandService {
    private final CommandRepository commandRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void guardianInstruct(InstructionRequest request, CurrentUser currentUser) {
        User ward = userService.getWardFromGuardianId(currentUser.getId());
        Command command = new Command(request.content(), CommandType.GUARDIAN_INSTRUCTION, ward);
        commandRepository.save(command);
        eventPublisher.publishEvent(new GuardianInstructed(command.getId(), command.getContent(), ward.getId()));

    }
}
