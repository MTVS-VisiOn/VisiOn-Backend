package mtvs.onvision.vision.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.response.ApiResponses;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponses<Void>> signup(@RequestBody @Valid SignupRequest request) {
        if (request.role() == UserRole.GUARDIAN) {
            if (request.wardId() == null) throw new BusinessException(ErrorCode.INVALID_WARD);
        }
        userService.signup(request);
        return ApiResult.ok(SuccessCode.USER_CREATED);
    }
}
