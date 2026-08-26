package mtvs.onvision.vision.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Poi(
        String id,
        String pkey,
        String name,
        Double noorLat,
        Double noorLon,
        String upperAddrName,
        String middleAddrName,
        String lowerAddrName,
        String detailAddrName,
        String firstNo,
        String secondNo,
        NewAddressList newAddressList
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewAddressList(
            List<NewAddress> newAddress
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NewAddress(
            String roadName,          // 비어 있으면 도로명 주소가 없는 POI다
            String fullAddressRoad
    ) {}
}
