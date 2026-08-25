package mtvs.onvision.vision.navigation.dto;

import java.util.List;

public record TmapTransitRequest(
        String startX,  // X가 경도
        String startY,
        String endX,
        String endY,
        Integer count,                  // 후보 개수. 자르기일 뿐이라 10 권장
        Integer lang,                   // 0
        String format
) {

    public static TmapTransitRequest from (NavigationPreRequest request) {
        // 목적지는 보행자 입구점 우선. 규칙은 LocationInfo.routingCoordinate 한 곳에만 둔다
        List<Double> end = request.end().routingCoordinate(request.mode());
        return new TmapTransitRequest(
                request.start().longitude().toString(), request.start().latitude().toString(),
                end.getLast().toString(), end.getFirst().toString(),
                10, 0,"json");
    }
}
