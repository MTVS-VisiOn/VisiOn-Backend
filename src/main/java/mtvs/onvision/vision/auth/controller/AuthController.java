package mtvs.onvision.vision.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.auth.dto.RefreshRequest;
import mtvs.onvision.vision.common.response.ApiResponses;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.auth.dto.LoginRequest;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponses<KeyPair>> login(@RequestBody @Valid LoginRequest request) {
        KeyPair response = userService.login(request);
        return ApiResult.ok(SuccessCode.LOGIN_SUCCESS,  response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponses<KeyPair>> login(@RequestBody @Valid RefreshRequest request) {
        KeyPair response = userService.refreshToken(request);
        return ApiResult.ok(SuccessCode.REFRESH_SUCCESS, response);
    }

    @DeleteMapping("/logout")
    public ResponseEntity<ApiResponses<SuccessCode>> logout(@AuthenticationPrincipal CurrentUser currentUser) {
        userService.logout(currentUser);
        return ApiResult.ok(SuccessCode.LOGOUT_SUCCESS);
    }
}
