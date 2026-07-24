package mtvs.onvision.vision.location.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.location.dto.LastLocationResponse;
import mtvs.onvision.vision.location.dto.LocationRequest;
import mtvs.onvision.vision.location.service.LocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService locationService;

    @PostMapping
    public ResponseEntity<ApiResult<Void>> receiveLocation(@RequestBody @Valid LocationRequest request,
                                                           @AuthenticationPrincipal CurrentUser currentUser) {
        locationService.receiveLocation(request, currentUser);
        return ApiResult.created(SuccessCode.LOCATION_CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResult<LastLocationResponse>> getLastLocation(@AuthenticationPrincipal CurrentUser currentUser) {
        LastLocationResponse response = locationService.getLastLocation(currentUser);
        return ApiResult.ok(SuccessCode.LOCATION_READ, response);
    }
}
