package mtvs.onvision.vision.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.auth.dto.LoginRequest;
import mtvs.onvision.vision.auth.dto.RefreshRequest;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerSupporter {
    private final UserService userService;

    @Override
    @PostMapping("/login")
    public ResponseEntity<ApiResult<KeyPair>> login(@RequestBody @Valid LoginRequest request) {
        KeyPair response = userService.login(request);
        return ApiResult.ok(SuccessCode.LOGIN_SUCCESS,  response);
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<ApiResult<KeyPair>> refreshToken(@RequestBody @Valid RefreshRequest request) {
        KeyPair response = userService.refreshToken(request);
        return ApiResult.ok(SuccessCode.REFRESH_SUCCESS, response);
    }

    @Override
    @DeleteMapping("/logout")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<SuccessCode>> logout(@AuthenticationPrincipal CurrentUser currentUser) {
        userService.logout(currentUser);
        return ApiResult.ok(SuccessCode.LOGOUT_SUCCESS);
    }
}
