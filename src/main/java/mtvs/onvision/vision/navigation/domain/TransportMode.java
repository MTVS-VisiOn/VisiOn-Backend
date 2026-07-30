package mtvs.onvision.vision.navigation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransportMode {
    WALK("/tmap/routes/pedestrian","4", "도보","walk:"),
    CAR("/tmap/routes", "0","차량","car:"),
    TRANSIT("/transit/routes", null,"대중교통","transit:");

    private final String path;
    private final String option;
    private final String description;
    private final String prefix;
}
