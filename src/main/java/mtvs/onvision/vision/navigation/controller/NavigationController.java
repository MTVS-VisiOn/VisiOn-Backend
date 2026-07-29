package mtvs.onvision.vision.navigation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.navigation.dto.NavigationPreRequest;
import mtvs.onvision.vision.navigation.dto.NavigationSummary;
import mtvs.onvision.vision.navigation.service.NavigationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/navigations")
@RequiredArgsConstructor
public class NavigationController {
    private final NavigationService navigationService;

    //출발지,도착지 받고 경로 찾기
    @PostMapping
    public ResponseEntity<ApiResult<NavigationSummary>> searchNavigation(@RequestBody @Valid NavigationPreRequest request,
                                                                           @AuthenticationPrincipal CurrentUser currentUser) {
        NavigationSummary response = navigationService.searchNavigation(request, currentUser);
        return ApiResult.ok(SuccessCode.BUSINESS_SUCCESS, response);
    }
}
