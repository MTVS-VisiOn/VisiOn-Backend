package mtvs.onvision.vision.navigation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import mtvs.onvision.vision.navigation.domain.TransportMode;
import mtvs.onvision.vision.navigation.dto.*;
import mtvs.onvision.vision.navigation.service.NavigationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/navigations")
@RequiredArgsConstructor
public class NavigationController implements NavigationControllerSupporter {
    private final NavigationService navigationService;

    //출발지,도착지 받고 경로 찾기(보행자, 자동차)
    @Override
    @PostMapping("/search")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<NavigationSummary>> searchNavigation(@RequestBody @Valid NavigationPreRequest request,
                                                                           @AuthenticationPrincipal CurrentUser currentUser) {
        if (request.mode() == TransportMode.TRANSIT) throw new BusinessException(ErrorCode.INVALID_TRANSFER);
        NavigationSummary response = navigationService.searchNavigation(request, currentUser);
        return ApiResult.ok(SuccessCode.NAVIGATION_SEARCH, response);
    }

    //출발지,도착지 받고 경로 찾기(대중교통)
    @Override
    @PostMapping("/search/transit")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<List<NavigationSummary>>> searchNavigationTransit(@RequestBody @Valid NavigationPreRequest request,
                                                                                      @AuthenticationPrincipal CurrentUser currentUser) {
        if (request.mode() != TransportMode.TRANSIT) throw new BusinessException(ErrorCode.INVALID_TRANSFER);
        List<NavigationSummary> response = navigationService.searchNavigationTransit(request, currentUser);
        return ApiResult.ok(SuccessCode.NAVIGATION_SEARCH, response);
    }

    //경로 선택
    @Override
    @PostMapping
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> saveRoute(@RequestBody @Valid RouteRequest request,
                                                     @AuthenticationPrincipal CurrentUser currentUser) {
        if (request.mode() == TransportMode.TRANSIT && request.index() == null) throw new BusinessException(ErrorCode.INVALID_TRANSIT_INDEX);
        navigationService.saveRoute(request, currentUser);
        return ApiResult.created(SuccessCode.ROUTE_CREATED);
    }

    //진행중인 경로 조회
    @Override
    @GetMapping("/processing")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<NavigationResponse>> getProcessingRoute(@AuthenticationPrincipal CurrentUser currentUser) {
        NavigationResponse response = navigationService.getProcessingRoute(currentUser);
        return ApiResult.ok(SuccessCode.ROUTE_READ, response);
    }

    //경로 완료
    @Override
    @PatchMapping("/complete")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> completeRoute(@AuthenticationPrincipal CurrentUser currentUser) {
        navigationService.completeRoute(currentUser);
        return ApiResult.ok(SuccessCode.ROUTE_COMPLETED);
    }

    //경로 취소
    @Override
    @PatchMapping("/cancel")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> cancelRoute(@AuthenticationPrincipal CurrentUser currentUser) {
        navigationService.cancelRoute(currentUser);
        return ApiResult.ok(SuccessCode.ROUTE_CANCELED);
    }

    //지도 표시를 위한 경로 조회
    @Override
    @GetMapping("/map")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<MapResponse>> getMapRoute(@AuthenticationPrincipal CurrentUser currentUser) {
        MapResponse response = navigationService.getMapRoute(currentUser);
        return ApiResult.ok(SuccessCode.ROUTE_READ, response);
    }
}
