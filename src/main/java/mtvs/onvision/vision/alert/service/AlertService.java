package mtvs.onvision.vision.alert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.alert.domain.Alert;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.dto.AlertResponse;
import mtvs.onvision.vision.alert.dto.ObstacleRequest;
import mtvs.onvision.vision.alert.event.ObstacleDetected;
import mtvs.onvision.vision.alert.repository.AlertRepository;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.image.service.ImageService;
import mtvs.onvision.vision.location.service.LocationService;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;
    private final LocationService locationService;
    private final ImageService imageService;


    @Transactional
    public void detectObstacle(ObstacleRequest request, MultipartFile image, CurrentUser currentUser) {
        User sender = userService.currentUserToUser(currentUser.getId());
        String address = locationService.getRoadAddress(request.latitude(), request.longitude());
        AlertType type = AlertType.OBSTACLE;
        String s3Key = imageService.saveImage(image, type);
        //알림 저장이 안된다면 s3도 보상삭제
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if(status == STATUS_ROLLED_BACK) {
                    try{
                        imageService.deleteImage(s3Key);
                    } catch(Exception e){
                        log.error("S3 cleanup failed after rollback: {}", e.getMessage());
                    }
                }
            }
        });
        Alert alert = new Alert(type, request.message(), request.latitude(), request.longitude(),
                address, s3Key, request.occurredAt(), request.action(), sender);
        alertRepository.save(alert);
        eventPublisher.publishEvent(new ObstacleDetected(alert.getId(), currentUser.getId()));
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlertDetail(Long alertId, CurrentUser currentUser) {
        Alert alert = alertRepository.findById(alertId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ALERT));
        Long wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        PreConditions.check(!alert.getSender().getId().equals(wardId), ErrorCode.NOT_GUARDIAN);
        String presignedUrl = imageService.getPresignedUrl(alert.getS3Key());
        return AlertResponse.from(alert, presignedUrl);
    }
}
