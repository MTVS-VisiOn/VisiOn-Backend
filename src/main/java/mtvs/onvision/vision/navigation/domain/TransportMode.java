package mtvs.onvision.vision.navigation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransportMode {
    WALK("/tmap/routes/pedestrian","4", "도보"),
    CAR("/tmap/routes", "0","차량");

    private final String path;
    private final String option;
    private final String description;
}
