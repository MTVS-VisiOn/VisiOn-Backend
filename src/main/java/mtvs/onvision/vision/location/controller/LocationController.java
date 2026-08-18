package mtvs.onvision.vision.location.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.location.dto.LastLocationResponse;
import mtvs.onvision.vision.location.dto.LocationRequest;
import mtvs.onvision.vision.location.dto.LocationSearchResponse;
import mtvs.onvision.vision.location.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController implements LocationControllerSupporter{
    private final LocationService locationService;

    @Override
    @PostMapping
    @ApiUnauthorized
    public ResponseEntity<ApiResult<Void>> receiveLocation(@RequestBody @Valid LocationRequest request,
                                                           @AuthenticationPrincipal CurrentUser currentUser) {
        locationService.receiveLocation(request, currentUser);
        return ApiResult.created(SuccessCode.LOCATION_CREATED);
    }

    @Override
    @GetMapping
    @ApiUnauthorized
    public ResponseEntity<ApiResult<LastLocationResponse>> getLastLocation(@AuthenticationPrincipal CurrentUser currentUser) {
        LastLocationResponse response = locationService.getLastLocation(currentUser);
        return ApiResult.ok(SuccessCode.LOCATION_READ, response);
    }

    @Override
    @GetMapping("/search")
    @ApiUnauthorized
    public ResponseEntity<ApiResult<LocationSearchResponse>> searchLocation(@RequestParam String keyword,
                                                                            @AuthenticationPrincipal CurrentUser currentUser) {
        PreConditions.check(keyword == null || keyword.isBlank(), ErrorCode.VALIDATION_FAILED, "keyword는 필수값입니다.");
        PreConditions.check(keyword.length()>100, ErrorCode.VALIDATION_FAILED, "keyword는 100자 이하여야 합니다.");
        LocationSearchResponse response = locationService.searchLocation(keyword, currentUser);
        return ApiResult.ok(SuccessCode.LOCATION_SEARCH_READ, response);
    }
}
