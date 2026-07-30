package mtvs.onvision.vision.location.dto;

public record LastLocationResponse(
        Boolean isConnected,
        String lastAddress,
        String status
) {
}
