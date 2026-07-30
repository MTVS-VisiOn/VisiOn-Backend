package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapPedestrianResponse(
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
            String name,
            String description,

            /* type이 Point일 때 */
            Integer pointIndex,
            RouteStepType pointType,          // SP:출발지, EP:도착지, PP~PP5:경유지, GP:일반 안내점
            Integer turnType,          // 127:계단 진입, 211~217:횡단보도, 218:엘리베이터 등
            String guidePointName,
            String direction,
            String intersectionName,
            String crossName,
            String nearPoiName,
            String nearPoiX,           // 문서상 String("0.0")
            String nearPoiY,
            Integer totalDistance,     // pointType=SP 에만 옴 (단위 m)
            Integer totalTime,         // pointType=SP 에만 옴 (단위 초)

            /* type이 LineString일 때 */
            Integer lineIndex,
            String roadName,
            Integer distance,          // 구간 거리(m)
            Integer time,              // 구간 소요시간(초)
            Integer roadType,          // 21~24 보행자도로 등급
            Integer categoryRoadType,  // 0:미분류, 1:특화거리, 2:테마거리, 3:청소년출입금지

            /* 공통 */
            String facilityType,       // 12:육교, 14:지하보도, 15:횡단보도 등
            String facilityName
    ) {}
}