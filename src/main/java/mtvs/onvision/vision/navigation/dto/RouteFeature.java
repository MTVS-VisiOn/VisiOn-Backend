package mtvs.onvision.vision.navigation.dto;

import tools.jackson.databind.JsonNode;

/**
 * toSteps가 쓰는 필드만 담은 내부 표현.
 * 보행자·자동차 응답을 여기로 옮긴 뒤 toSteps 한 벌로 처리한다.
 * pointType은 라벨로, facilityType은 FacilityType으로 변환된 상태다.
 */
public record RouteFeature(
        GeometryType type,
        JsonNode coordinates,
        String description,
        Integer turnType,
        String pointType,        // "출발지" / "일반 안내점"
        FacilityType facility,   // 모르는 값은 null
        Integer distance,        // LineString만
        Integer time             // LineString만
) {
    public static RouteFeature from(TmapPedestrianResponse.Feature f) {
        TmapPedestrianResponse.Properties p = f.properties();
        return new RouteFeature(
                f.geometry().type(),
                f.geometry().coordinates(),
                p.description(),
                p.turnType(),
                p.pointType() == null ? null : p.pointType().getDescription(),
                FacilityType.from(p.facilityType()),      // String 오버로드
                p.distance(),
                p.time());
    }

    public static RouteFeature from(TmapCarResponse.Feature f) {
        TmapCarResponse.Properties p = f.properties();
        return new RouteFeature(
                f.geometry().type(),
                f.geometry().coordinates(),
                p.description(),
                p.turnType(),
                p.pointType() == null ? null : p.pointType().getDescription(),
                FacilityType.from(p.facilityType()),      // Integer 오버로드
                p.distance(),
                p.time());
    }
}
