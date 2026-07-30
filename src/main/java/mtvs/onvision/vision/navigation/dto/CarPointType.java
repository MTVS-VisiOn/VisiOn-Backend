package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CarPointType {
    S("출발지"),
    E("도착지"),
    N("일반 안내점"),
    B1("경유지1"),
    B2("경유지2"),
    B3("경유지3");

    private final String description;

    @JsonCreator
    public static CarPointType from(String raw) {
        for (CarPointType pointType : CarPointType.values()) {
            if (pointType.name().equals(raw)) {
                return pointType;
            }
        }
        return null;
    }
}
