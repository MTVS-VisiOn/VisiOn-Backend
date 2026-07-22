package mtvs.onvision.vision.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.ResisterGuardianResponse;
import mtvs.onvision.vision.user.dto.SettingRequest;
import mtvs.onvision.vision.user.dto.SignupRequest;
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

    @GetMapping("/guardian/register-token")
    public ResponseEntity<ApiResult<ResisterGuardianResponse>> getGuardianRegisterToken(@AuthenticationPrincipal CurrentUser currentUser) {
        ResisterGuardianResponse response = userService.getGuardianRegisterToken(currentUser);
        return ApiResult.ok(SuccessCode.REGISTER_TOKEN_CREATED, response);
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResult<Void>> updateGuardianSettings(@RequestBody @Valid SettingRequest request,
                                                                  @AuthenticationPrincipal CurrentUser currentUser) {
        userService.updateGuardianSettings(request, currentUser);
        return ApiResult.ok(SuccessCode.BUSINESS_SUCCESS);
    }
}
