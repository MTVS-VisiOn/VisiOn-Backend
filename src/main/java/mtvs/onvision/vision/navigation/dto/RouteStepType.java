package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RouteStepType {
    SP("출발지"),
    EP("도착지"), 
    PP("경유지"),
    PP1("경유지1"),
    PP2("경유지2"),
    PP3("경유지3"),
    PP4("경유지4"),
    PP5("경유지5"),
    GP("일반 안내점"),
    FP("시설 안내점");  //우리가 만든 타입

    private final String description;

    @JsonCreator
    public static RouteStepType from(String raw) {
        for (RouteStepType pointType : RouteStepType.values()) {
            if (pointType.name().equals(raw)) {
                   return pointType;
            }
        }
        return null;
   }
}
