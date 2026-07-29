package mtvs.onvision.vision.navigation.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 구간(LineString)의 시설물 타입. 보행자와 자동차가 값을 공유한다.
 * 겹치는 1·2·3은 양쪽에서 뜻이 같고, 나머지는 값이 부딪히지 않는다.
 * 보행자는 문자열("15"), 자동차는 숫자(15)로 온다.
 */
@Getter
@RequiredArgsConstructor
public enum FacilityType {
    /* 공통 */
    BRIDGE("1", "교량", "다리를 건너세요"),
    TUNNEL("2", "터널", "터널을 지나세요"),
    ELEVATED_ROAD("3", "고가도로", "고가도로를 지나세요"),

    /* 보행자 */
    WALKWAY("11", "일반보행자도로", "계속 이동하세요"),
    OVERPASS("12", "육교", "육교를 건너세요"),
    UNDERPASS("14", "지하보도", "지하보도로 들어가세요"),
    CROSSWALK("15", "횡단보도", "횡단보도를 건너세요"),
    INDOOR_PASSAGE("16", "대형시설물이동통로", "건물 안 통로로 들어가세요"),
    STAIRS("17", "계단", "계단을 이용하세요"),
    SUBWAY_UNDERPASS("18", "지하철지하보도", "지하철 지하보도로 들어가세요"),

    /* 자동차 — message는 시설 분할을 안 하므로 쓰이지 않음 */
    NORMAL("0", "일반도로", "일반도로"),
    UNDERGROUND_ROAD("4", "지하도로", "지하도로"),
    INTERSECTION("5", "교차로통과", "교차로통과"),
    RAILROAD_CROSSING("6", "철도건널목", "철도건널목"),
    DAM("7", "댐/방파제", "댐/방파제"),
    RABBIT_HOLE("13", "토끼굴", "토끼굴"),
    HAN_RIVER_BRIDGE("90", "한강교량", "한강교량");

    private final String value;
    private final String label;     // RouteStep.facility에 나가는 값
    private final String message;   // FP step의 description

    public boolean matches(String raw) {
        return value.equals(raw);
    }

    public static FacilityType from(String raw) {
        for (FacilityType facilityType : FacilityType.values()) {
            if (facilityType.value.equals(raw)) {
                return facilityType;
            }
        }
        return null;
    }

    // 자동차는 숫자로 온다
    public static FacilityType from(Integer raw) {
        return raw == null ? null : from(String.valueOf(raw));
    }
}
