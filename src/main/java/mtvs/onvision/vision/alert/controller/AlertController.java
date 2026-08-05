package mtvs.onvision.vision.alert.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.alert.dto.ObstacleRequest;
import mtvs.onvision.vision.alert.service.AlertService;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/alerts")
public class AlertController implements AlertControllerSupporter {
    private final AlertService alertService;

    @Override
    @PostMapping("/detect/obstacle")
    public ResponseEntity<ApiResult<Void>> detectObstacle(@RequestPart(name = "request") @Valid ObstacleRequest request,
                                                     @RequestPart("image") MultipartFile image,
                                                     @AuthenticationPrincipal CurrentUser currentUser) {
        alertService.detectObstacle(request, image, currentUser);
        return ApiResult.ok(SuccessCode.DETECT_OBSTACLE_CREATED);
    }

}
