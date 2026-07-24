package mtvs.onvision.vision.location.dto;

public record LastLocationResponse(
        Boolean isCponnected,
        String lastAddress,
        String status
) {
}
