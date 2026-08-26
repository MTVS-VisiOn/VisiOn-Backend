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
        String roadAddress = roadAddressOf(poi);
        return new LocationSearchInfo(poi.id(), poi.pkey(), poi.name(), poi.noorLat(), poi.noorLon(), landAddress, roadAddress);
    }
    /**
     * 도로명 주소가 없는 POI가 있다. 세 단계(newAddressList / newAddress / 첫 원소)가 모두 결손 가능하다.
     * 그 한 건 때문에 검색 결과 전체를 버리지 않는다 — 주소는 부가 정보이고 landAddress는 남는다.
     */
    private static String roadAddressOf(Poi poi) {
        Poi.NewAddressList list = poi.newAddressList();
        if (list == null || list.newAddress() == null || list.newAddress().isEmpty()) return null;
        return list.newAddress().getFirst().fullAddressRoad();
    }
    private static boolean hasValue(String no) {
        return no != null && !no.isBlank() && !no.equals("0");
    }
}
