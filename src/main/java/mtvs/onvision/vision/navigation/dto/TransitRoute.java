package mtvs.onvision.vision.navigation.dto;

import java.util.List;

/**
 * 대중교통 경로 후보 하나. Redis에는 이것의 리스트가 JSON 배열로 들어간다.
 * <p>
 * 사용자가 후보를 고르는 시점이 검색 이후라 "검색 = 저장"이 안 된다.
 * 하루 10건 한도 때문에 선택 시점에 재조회를 할 수 없어서 후보 전체를 미리 들고 있어야 한다.
 * <p>
 * 검색 시각이 필요하면 Redis의 남은 TTL로 계산한다. 별도 필드를 두지 않는다.
 */
public record TransitRoute(
        TransitSummaryResponse summary,
        List<TransitLeg> legs
) {

    public record TransitLeg(
            Integer sequence,
            String mode,              // 원문. WALK BUS SUBWAY EXPRESSBUS TRAIN AIRPLANE FERRY
            String route,
            String routeId,
            Integer type,
            String routeColor,
            List<String> routes,      // 대표 노선 + Lane (service 0 제외)
            Integer sectionTime,      // 초
            Integer distance,         // m
            Integer routePayment,     // 광역 이동수단 요금(원). 나머지는 null
            String startName,
            List<Double> startCoordinate,   // [위도, 경도]
            String endName,
            List<Double> endCoordinate,
            List<TransitStation> stations,  // 대중교통 leg만
            List<TransitStep> steps,        // 첫·마지막 도보 leg만. 환승 도보는 빈 리스트
            List<List<Double>> path         // passShape을 [위도, 경도]로 뒤집은 것
    ) {}

    public record TransitStation(
            Integer index,
            String stationId,
            String stationName,
            List<Double> coordinate   // [위도, 경도]
    ) {}

    public record TransitStep(
            Integer sequence,
            String description,
            Integer distance,
            String streetName,
            List<List<Double>> path
    ) {}
}
