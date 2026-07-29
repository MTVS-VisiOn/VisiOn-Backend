package mtvs.onvision.vision.navigation.dto;

import java.util.List;

public record NavigationRouteReport(
        NavigationSummaryResponse summary,
        List<RouteStep> report
) {
}
