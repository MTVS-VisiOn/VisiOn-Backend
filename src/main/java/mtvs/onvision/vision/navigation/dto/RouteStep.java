package mtvs.onvision.vision.navigation.dto;

import java.util.List;

public record RouteStep(
        Integer sequence,
        Double latitude,
        Double longitude,
        String description,
        Integer turnType,
        String  pointType,  //SP, GP, PP1,EP
        String  facility, // 다음 구간이 계단인지 횡단보도인지
        Integer distanceToNext,
        Integer timeToNext,
        Integer cumulativeDistance,
        List<List<Double>> pathToNext

        ) {
}
