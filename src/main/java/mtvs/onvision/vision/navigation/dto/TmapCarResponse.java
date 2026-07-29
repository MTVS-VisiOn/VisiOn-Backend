package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapCarResponse(
        String type,
        List<Feature> features
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(
            String type,
            Geometry geometry,
            Properties properties
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(
            // Point이면 [경도, 위도], LineString이면 [[경도, 위도], ...] 이라 record로 못 받음
            GeometryType type,
            JsonNode coordinates
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            Integer index,
            String name,               // 보행자와 달리 교차로·IC 이름이 들어온다
            String description,

            /* type이 Point일 때 */
            Integer pointIndex,
            CarPointType pointType,    // S:출발지, E:도착지, N:일반 안내점, B1~B3:경유지
            Integer turnType,          // 12:좌회전, 13:우회전, 119:지하차도, 120:고가도로, 121:터널, 200:출발, 201:도착
            String nextRoadName,
            Integer totalDistance,     // pointType=S 에만 옴 (단위 m)
            Integer totalTime,         // pointType=S 에만 옴 (단위 초)
            Integer totalFare,         // pointType=S 에만 옴 (통행료, 원)
            Integer taxiFare,          // pointType=S 에만 옴 (택시 예상요금, 원)

            /* type이 LineString일 때 */
            Integer lineIndex,
            Integer distance,          // 구간 거리(m)
            Integer time,              // 구간 소요시간(초)
            Integer roadType,          // 0:고속도로, 5~8:일반도로 등급. 보행자(21~24)와 대역이 다름
            Integer facilityType       // 보행자는 String, 자동차는 숫자. 1:교량, 2:터널, 4:지하도로
    ) {}
}
