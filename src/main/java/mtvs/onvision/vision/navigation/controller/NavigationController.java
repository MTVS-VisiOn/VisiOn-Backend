package mtvs.onvision.vision.navigation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.navigation.domain.TransportMode;
import mtvs.onvision.vision.navigation.dto.NavigationPreRequest;
import mtvs.onvision.vision.navigation.dto.NavigationSummary;
import mtvs.onvision.vision.navigation.dto.NavigationResponse;
import mtvs.onvision.vision.navigation.dto.RouteRequest;
import mtvs.onvision.vision.navigation.service.NavigationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/navigations")
@RequiredArgsConstructor
public class NavigationController {
    private final NavigationService navigationService;

    //출발지,도착지 받고 경로 찾기(보행자, 자동차)
    @PostMapping("/search")
    public ResponseEntity<ApiResult<NavigationSummary>> searchNavigation(@RequestBody @Valid NavigationPreRequest request,
                                                                           @AuthenticationPrincipal CurrentUser currentUser) {
        if (request.mode() == TransportMode.TRANSIT) throw new BusinessException(ErrorCode.INVALID_TRANSFER);
        NavigationSummary response = navigationService.searchNavigation(request, currentUser);
        return ApiResult.ok(SuccessCode.NAVIGATION_SEARCH, response);
    }

    //출발지,도착지 받고 경로 찾기(대중교통)
    @PostMapping("/search/transit")
    public ResponseEntity<ApiResult<List<NavigationSummary>>> searchNavigationTransit(@RequestBody @Valid NavigationPreRequest request,
                                                                                      @AuthenticationPrincipal CurrentUser currentUser) {
        if (request.mode() != TransportMode.TRANSIT) throw new BusinessException(ErrorCode.INVALID_TRANSFER);
        List<NavigationSummary> response = navigationService.searchNavigationTransit(request, currentUser);
        return ApiResult.ok(SuccessCode.NAVIGATION_SEARCH, response);
    }

    //경로 선택
    @PostMapping
    public ResponseEntity<ApiResult<Void>> saveRoute(@RequestBody @Valid RouteRequest request,
                                                     @AuthenticationPrincipal CurrentUser currentUser) {
        if (request.mode() == TransportMode.TRANSIT && request.index() == null) throw new BusinessException(ErrorCode.INVALID_TRANSIT_INDEX);
        navigationService.saveRoute(request, currentUser);
        return ApiResult.ok(SuccessCode.ROUTE_CREATED);
    }

    //진행중인 경로 조회
    @GetMapping("/processing")
    public ResponseEntity<ApiResult<NavigationResponse>> getProcessingRoute(@AuthenticationPrincipal CurrentUser currentUser) {
        NavigationResponse response = navigationService.getProcessingRoute(currentUser);
        return ApiResult.ok(SuccessCode.ROUTE_READ, response);
    }
}
