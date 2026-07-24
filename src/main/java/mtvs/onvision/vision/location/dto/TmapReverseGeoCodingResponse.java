package mtvs.onvision.vision.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapReverseGeoCodingResponse(
        AddressInfo addressInfo
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressInfo(
            String fullAddress,                            // 합쳐진 전체 주소(구분자 ;)
            String addressType,                            // 예: A10
            @JsonProperty("city_do")  String cityDo,       // 시/도
            @JsonProperty("gu_gun")   String guGun,        // 구/군
            @JsonProperty("eup_myun") String eupMyun,      // 읍/면
            String adminDong,                              // 행정동
            String legalDong,                              // 법정동
            String ri,                                     // 리
            String bunji,                                  // 번지
            String roadName,                               // 도로명
            String buildingIndex,                          // 건물번호
            String buildingName                            // 건물명
    ) {}
}
