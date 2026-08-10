package mtvs.onvision.vision.command.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.dto.InstructionResponse;
import mtvs.onvision.vision.command.service.InstructionService;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/instructions")
public class InstructionController implements InstructionControllerSupporter {
    private final InstructionService instructionService;

    @Override
    @PostMapping
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> saveInstruction(@RequestBody @Valid InstructionRequest request,
                                                           @AuthenticationPrincipal CurrentUser currentUser) {

        instructionService.saveInstruction(request, currentUser);
        return ApiResult.created(SuccessCode.INSTRUCTION_CREATED);
    }

    @Override
    @GetMapping
    @ApiUnauthorized
    public ResponseEntity<ApiResult<List<InstructionResponse>>> getInstructions(@AuthenticationPrincipal CurrentUser currentUser) {
        List<InstructionResponse> response = instructionService.getInstructions(currentUser);
        return ApiResult.ok(SuccessCode.INSTRUCTION_READ, response);
    }

    @Override
    @PutMapping("/{instructionId}")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> updateInstruction(@PathVariable Long instructionId,
                                                             @RequestBody @Valid InstructionRequest request,
                                                             @AuthenticationPrincipal CurrentUser currentUser) {
        instructionService.updateInstruction(instructionId, request, currentUser);
        return ApiResult.ok(SuccessCode.INSTRUCTION_UPDATED);
    }

    @Override
    @DeleteMapping("/{instructionId}")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> deleteInstruction(@PathVariable Long instructionId,
                                                             @AuthenticationPrincipal CurrentUser currentUser) {
        instructionService.deleteInstruction(instructionId, currentUser);
        return ApiResult.ok(SuccessCode.INSTRUCTION_DELETED);
    }
}
