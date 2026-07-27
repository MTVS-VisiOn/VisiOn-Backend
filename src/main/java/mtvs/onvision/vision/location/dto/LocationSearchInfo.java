package mtvs.onvision.vision.location.dto;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public record LocationSearchInfo(
        String id,
        String pkey,
        String name,
        Double noorLat,
        Double noorLon,
        String landAddress,
        String roadAddress
){
    public static LocationSearchInfo from(Poi poi){
        String bunji = "";
        if (hasValue(poi.firstNo())) {
            bunji = poi.firstNo();
            if (hasValue(poi.secondNo())) {
                bunji += "-" + poi.secondNo();
            }
        }

        String landAddress = Stream.of(
                        poi.upperAddrName(), poi.middleAddrName(),
                        poi.lowerAddrName(), bunji, poi.detailAddrName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        String roadAddress = poi.newAddressList().newAddress().getFirst().fullAddressRoad();
        return new LocationSearchInfo(poi.id(), poi.pkey(), poi.name(), poi.noorLat(), poi.noorLon(), landAddress, roadAddress);
    }
    private static boolean hasValue(String no) {
        return no != null && !no.isBlank() && !no.equals("0");
    }
}
