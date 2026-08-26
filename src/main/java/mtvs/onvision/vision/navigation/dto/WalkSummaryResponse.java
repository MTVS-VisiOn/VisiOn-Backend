package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.util.List;

public record WalkSummaryResponse(
    Integer index,
    TransportMode mode,
    Integer totalDistance,  //총걸리는 시간
    Integer totalTime,   //초
    Integer crosswalkCount,   //LineString facilityType "15" 구간 수
    Integer stairsCount,   //LineString facilityType "17" 구간 수
    Integer overpassCount,
    Integer underpassCount  ,   //LineString facilityType "14" 구간 수
    String startingName,  //출발지
    String startingAddress,  //출발지 주소
    List<Double> startingCoordinate,  //출발지 좌표
    String destinationName,  //도착지
    String destinationAddress,   //도착지 주소
    List<Double> destinationCoordinate  //도착지 좌표
) implements NavigationSummary {
}
