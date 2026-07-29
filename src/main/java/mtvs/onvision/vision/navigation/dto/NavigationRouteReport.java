package mtvs.onvision.vision.navigation.dto;

import java.util.List;

public record NavigationRouteReport(
        NavigationSummary summary,
        List<RouteStep> report
) {
}
