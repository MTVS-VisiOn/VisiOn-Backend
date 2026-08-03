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
import mtvs.onvision.vision.user.dto.ResisterGuardianResponse;
import mtvs.onvision.vision.user.dto.SettingRequest;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.dto.UserResponse;
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
            if (request.registerToken() == null) throw new BusinessException(ErrorCode.INVALID_WARD);
        }
        userService.signup(request);
        return ApiResult.created(SuccessCode.USER_CREATED);
    }

    @Override
    @GetMapping("/guardian/register-token")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<ResisterGuardianResponse>> getGuardianRegisterToken(@AuthenticationPrincipal CurrentUser currentUser) {
        ResisterGuardianResponse response = userService.getGuardianRegisterToken(currentUser);
        return ApiResult.ok(SuccessCode.REGISTER_TOKEN_CREATED, response);
    }

    @Override
    @PutMapping("/settings")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> updateGuardianSettings(@RequestBody @Valid SettingRequest request,
                                                                  @AuthenticationPrincipal CurrentUser currentUser) {
        userService.updateGuardianSettings(request, currentUser);
        return ApiResult.ok(SuccessCode.SETTING_SUCCESS);
    }

    //보호자 계정 정보 조회
    @Override
    @GetMapping("/me")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<UserResponse>> getUserInfo(@AuthenticationPrincipal CurrentUser currentUser) {
        UserResponse response = userService.getUserInfo(currentUser);
        return ApiResult.ok(SuccessCode.USER_READ, response);
    }
}
