package mtvs.onvision.vision.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapPoiSearchResponse(
        SearchPoiInfo searchPoiInfo
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchPoiInfo(
            Integer totalCount,
            Integer count,
            Integer page,
            Pois pois
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pois(
            List<Poi> poi
    ) {}
}
