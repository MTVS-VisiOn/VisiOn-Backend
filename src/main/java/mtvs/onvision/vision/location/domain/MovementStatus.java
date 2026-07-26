package mtvs.onvision.vision.location.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MovementStatus {
    STATIONARY("멈춤"),
    ON_FOOT("도보로 이동중"),
    IN_VEHICLE("차량 이동중"),
    UNKNOWN("알수없음");

    private final String message;
}
