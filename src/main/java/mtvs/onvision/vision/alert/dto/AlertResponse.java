package mtvs.onvision.vision.alert.dto;

import mtvs.onvision.vision.alert.domain.Alert;
import mtvs.onvision.vision.alert.domain.AlertType;

import java.time.LocalDateTime;

import static mtvs.onvision.vision.alert.service.AlertService.SEOUL;

public record AlertResponse(
      AlertType type,
      LocalDateTime occurredAt,
      String occurredPlace,
      String presignedUrl,
      String content,
      String action
) {
    public static AlertResponse from(Alert alert, String presignedUrl) {
        return new AlertResponse(alert.getType(), alert.getOccurredAt().atZone(SEOUL).toLocalDateTime(), alert.getAddress(),  presignedUrl, alert.getContent(), alert.getAction());
    }
}
