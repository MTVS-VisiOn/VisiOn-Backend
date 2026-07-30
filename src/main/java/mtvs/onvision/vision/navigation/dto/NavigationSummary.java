package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "mode", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WalkSummaryResponse.class, name = "WALK"),
        @JsonSubTypes.Type(value = CarSummaryResponse.class,  name = "CAR"),
        @JsonSubTypes.Type(value = TransitSummaryResponse.class,  name = "TRANSIT")
})
public sealed interface NavigationSummary permits WalkSummaryResponse, CarSummaryResponse, TransitSummaryResponse {
    Integer index();
    Integer totalDistance();
    Integer totalTime();
    String startingName();
    String startingRoadAddress();
    List<Double> startingCoordinate();
    String destinationName();
    String destinationRoadAddress();
    List<Double> destinationCoordinate();

}
