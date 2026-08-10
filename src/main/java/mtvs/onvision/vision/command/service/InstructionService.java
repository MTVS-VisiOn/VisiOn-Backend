package mtvs.onvision.vision.command.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.domain.Instruction;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.repository.InstructionRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstructionService {
    private final InstructionRepository instructionRepository;
    private final UserService userService;

    @Transactional
    public void saveInstruction(InstructionRequest request, CurrentUser currentUser) {
        User guardian = userService.currentUserToUser(currentUser.getId());
        Instruction instruction = new Instruction(request.content(), guardian);
        instructionRepository.save(instruction);
    }
}
