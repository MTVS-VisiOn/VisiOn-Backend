package mtvs.onvision.vision.user.dto;

public record SettingRequest(
        Boolean offlineAlertEnabled,
        Boolean arrivalAlertEnabled
) {
}
