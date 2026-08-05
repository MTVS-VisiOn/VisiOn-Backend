package mtvs.onvision.vision.alert.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.alert.dto.AlertResponse;
import mtvs.onvision.vision.alert.dto.ObstacleRequest;
import mtvs.onvision.vision.alert.service.AlertService;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    //상세 알림 보기
    @Override
    @GetMapping("/{alertId}")
    public ResponseEntity<ApiResult<AlertResponse>> getAlertDetail(@PathVariable Long alertId,
                                                                   @AuthenticationPrincipal CurrentUser currentUser) {
        AlertResponse response = alertService.getAlertDetail(alertId, currentUser);
        return ApiResult.ok(SuccessCode.ALERT_READ, response);
    }

    //최근 일주일 이벤트 확인
    @GetMapping("/lastweek")
    public ResponseEntity<ApiResult<Map<LocalDate, List<AlertResponse>>>> getAlertsInWeek(@AuthenticationPrincipal CurrentUser currentUser) {
        Map<LocalDate, List<AlertResponse>> response = alertService.getAlertsInWeek(currentUser);
        return ApiResult.ok(SuccessCode.ALERT_READ, response);
    }

}
