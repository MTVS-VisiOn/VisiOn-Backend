package mtvs.onvision.vision.signalling.controller;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import mtvs.onvision.vision.signalling.dto.IceServersResponse;
import mtvs.onvision.vision.signalling.service.IceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ice-servers")
@RequiredArgsConstructor
public class IceController implements IceControllerSupporter{
    private final IceService iceService;

    @Override
    @GetMapping
    @ApiUnauthorized
    public ResponseEntity<ApiResult<IceServersResponse>> getIceServers(@AuthenticationPrincipal CurrentUser currentUser) {
        IceServersResponse response = iceService.getIceServers(currentUser);
        return ApiResult.ok(SuccessCode.ICE_SERVERS_READ, response);
    }
}
