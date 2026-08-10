package mtvs.onvision.vision.command.controller;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.dto.InstructionResponse;
import mtvs.onvision.vision.command.service.InstructionService;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instructions")
public class InstructionController {
    private final InstructionService instructionService;

    @PostMapping
    public ResponseEntity<ApiResult<Void>> saveInstruction(@RequestBody InstructionRequest request,
                                                           @AuthenticationPrincipal CurrentUser currentUser) {

        instructionService.saveInstruction(request, currentUser);
        return ApiResult.created(SuccessCode.INSTRUCTION_CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResult<List<InstructionResponse>>> getInstructions(@AuthenticationPrincipal CurrentUser currentUser) {
        List<InstructionResponse> response = instructionService.getInstructions(currentUser);
        return ApiResult.ok(SuccessCode.INSTRUCTION_READ, response);
    }
}
