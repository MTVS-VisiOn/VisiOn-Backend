package mtvs.onvision.vision.presence.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import mtvs.onvision.vision.presence.dto.HeartbeatRequest;
import mtvs.onvision.vision.presence.dto.PresenceResponse;
import mtvs.onvision.vision.presence.service.PresenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController implements PresenceControllerSupporter{
    private final PresenceService presenceService;

    @Override
    @PostMapping
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> receiveHeartBeat(@Valid @RequestBody HeartbeatRequest request,
                                                            @AuthenticationPrincipal CurrentUser currentUser) {
        presenceService.saveHeartBeat(request, currentUser);
        return ApiResult.created(SuccessCode.HEARTBEAT_CREATED);
    }

    @Override
    @GetMapping
    @ApiUnauthorized
    public ResponseEntity<ApiResult<PresenceResponse>> getWardPresence(@AuthenticationPrincipal CurrentUser currentUser) {
        PresenceResponse response = presenceService.getWardPresence(currentUser);
        return ApiResult.ok(SuccessCode.PRESENCE_READ, response);
    }
}
