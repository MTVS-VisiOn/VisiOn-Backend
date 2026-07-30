package mtvs.onvision.vision.navigation.dto;

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
        return new TmapTransitRequest(
                request.start().longitude().toString(), request.start().latitude().toString(),
                request.end().longitude().toString(), request.end().latitude().toString(),
                10, 0,"json");
    }
}
