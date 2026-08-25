package mtvs.onvision.vision.location.dto;

import mtvs.onvision.vision.common.util.GeoUtils;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * `noorLat`/`noorLon`은 POI 중심점, `pnsLat`/`pnsLon`은 보행자 입구점이다.
 * 길안내 목적지로는 입구점이 맞다 — 실측(`samples/poi-pangyo`)에서 둘이 건물 POI 기준 12~26m 벌어졌다.
 * 전면좌표(`frontLat`/`frontLon`)는 차량 진입 기준이라 싣지 않는다. 입구점과 30~71m 차이가 났다.
 *
 * 입구점은 티맵 2025-05 추가분이라 없을 수 있고, 그때는 null로 나간다.
 */
public record LocationSearchInfo(
        String id,
        String pkey,
        String name,
        Double noorLat,
        Double noorLon,
        Double pnsLat,
        Double pnsLon,
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
        // 값 없음이 0.0으로 오는 자리가 있어 범위까지 본다. 이상하면 클라이언트에 넘기지 않고 null로 내린다
        boolean hasEntrance = GeoUtils.isInKorea(poi.pnsLat(), poi.pnsLon());
        return new LocationSearchInfo(poi.id(), poi.pkey(), poi.name(), poi.noorLat(), poi.noorLon(),
                hasEntrance ? poi.pnsLat() : null, hasEntrance ? poi.pnsLon() : null,
                landAddress, roadAddress);
    }
    private static boolean hasValue(String no) {
        return no != null && !no.isBlank() && !no.equals("0");
    }
}
