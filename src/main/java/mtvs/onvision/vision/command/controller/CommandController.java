package mtvs.onvision.vision.command.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.service.CommandService;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/commands")
public class CommandController {
    private final CommandService commandService;

    @PostMapping("/instruction")
    public ResponseEntity<ApiResult<Void>> guardianInstruct(@RequestBody @Valid InstructionRequest request,
                                                               @AuthenticationPrincipal CurrentUser currentUser) {
        commandService.guardianInstruct(request, currentUser);
        return ApiResult.created(SuccessCode.COMMAND_CREATED);
    }
}
