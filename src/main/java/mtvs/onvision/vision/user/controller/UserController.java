package mtvs.onvision.vision.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.*;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserController implements UserControllerSupporter {

    private final UserService userService;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResult<Void>> signup(@RequestBody @Valid SignupRequest request) {
        if (request.role() == UserRole.GUARDIAN) {
            if (request.registerCode() == null) throw new BusinessException(ErrorCode.INVALID_WARD);
        }
        userService.signup(request);
        return ApiResult.created(SuccessCode.USER_CREATED);
    }

    @Override
    @GetMapping("/guardian/register-code")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<RegisterResponse>> getGuardianRegisterCode(@AuthenticationPrincipal CurrentUser currentUser) {
        RegisterResponse response = userService.getGuardianRegisterCode(currentUser);
        return ApiResult.ok(SuccessCode.REGISTER_CODE_CREATED, response);
    }

    @PostMapping("/device-fid")
    public ResponseEntity<ApiResult<Void>> checkDeviceFid(@RequestBody @Valid FidRequest request,
                                                          @AuthenticationPrincipal CurrentUser currentUser) {
        userService.checkDeviceFid(request, currentUser);
        return ApiResult.ok(SuccessCode.CHECK_FID);
    }

    //보호자 계정 정보 조회
    @Override
    @GetMapping("/me")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<UserResponse>> getUserInfo(@AuthenticationPrincipal CurrentUser currentUser) {
        UserResponse response = userService.getUserInfo(currentUser);
        return ApiResult.ok(SuccessCode.USER_READ, response);
    }

    @Override
    @GetMapping("/device/register-code")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<RegisterResponse>> getDeviceRegisterCode(@AuthenticationPrincipal CurrentUser currentUser) {
        RegisterResponse response = userService.getDeviceRegisterCode(currentUser);
        return ApiResult.ok(SuccessCode.REGISTER_CODE_CREATED, response);
    }

    @Override
    @PostMapping("/device/pairing/exchange")
    public ResponseEntity<ApiResult<PairingDeviceResponse>> pairingDevice(@RequestBody @Valid DeviceRegisterRequest request) {
        PairingDeviceResponse response = userService.pairingDevice(request);
        return ApiResult.ok(SuccessCode.PAIRING_SUCCESS, response);
    }
}
