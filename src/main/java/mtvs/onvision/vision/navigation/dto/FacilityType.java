package mtvs.onvision.vision.navigation.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FacilityType {
    NORMAL("11", "계속 이동하세요"),  //일반보행자도로
    OVERPASS("12","육교를 건너세요"),  //육교
    UNDERPASS("14","지하보도로 들어가세요"),   //지하보도
    CROSSWALK("15","횡단보도를 건너세요"),  //횡단보도
    STAIRS("17","계단을 이용하세요");        // 문서 미기재. searchOption 30에서 사라지는 것으로 확인

    private final String value;
    private final String message;

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
}
