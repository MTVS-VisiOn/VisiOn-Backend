package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mtvs.onvision.vision.common.util.GeoUtils;
import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.util.List;

public record LocationInfo(
        @NotBlank(message = "장소명은 필수입니다.")
        @Size(max = 50)
        @Schema(
                examples = "신논현역",
                description = "지도상의 장소명. 장소검색 응답의 name 또는 즐겨찾기의 name을 그대로 전달",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String name,

        @Size(max = 50)
        @Schema(
                examples = "회사",
                description = """
                        사용자가 붙인 별칭. 즐겨찾기에서 고른 장소면 그 별칭을, 아니면 name과 같은 값을 보낸다.
                        비어 있지 않으면 이 값이 응답의 startingName·destinationName으로 나간다""",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String nickname,

        @NotNull
        @Schema(
                examples = "37.504585",
                description = "위도. 한국은 33~38 범위다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Double latitude,

        @NotNull
        @Schema(
                examples = "127.024798",
                description = "경도. 한국은 124~132 범위다",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Double longitude,

        @NotBlank
        @Size(max = 100)
        @Schema(
                examples = "서울 강남구 강남대로 지하 476",
                description = """
                        표시용 주소. 도로명 주소가 있으면 그 값을, 없으면 지번 주소를 보낸다.
                        티맵이 도로명 없는 POI에 도로명을 주지 않으므로 장소검색 응답의
                        roadAddress는 null일 수 있다 — 그때 landAddress를 대신 넣는다""",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String address,

        @Schema(
                examples = "3",
                description = """
                        즐겨찾기에서 고른 장소면 그 id. 직접 검색한 장소면 null.
                        조회 응답에서는 항상 null이다 — 경로 저장 시점에 좌표와 이름만 복제하기 때문"""
        )
        Long favoriteId,

        @Schema(
                examples = "37.40075332",
                description = """
                        보행자 입구점 위도. 장소검색 응답의 `pnsLat`을 그대로 전달한다.

                        **목적지에만, 그리고 `WALK`·`TRANSIT`에만 쓰인다.** 값이 있으면 경로 탐색이
                        중심점 대신 이 좌표로 간다 — 건물 중심점은 벽 안쪽이라 안내가 끝나는 자리가
                        실제 출입구와 12~26m 어긋난다.

                        `CAR`는 이 값을 보내도 무시하고 `latitude`/`longitude`로 간다.
                        주차장 POI의 입구점이 주차장이 아니라 **본관 보행자 출입구**를 가리키기 때문이다
                        (실측 `samples/poi-haengjeong` — 자기 중심점과 27~33m 차이).

                        없으면 `latitude`/`longitude`로 폴백하므로 보내지 않아도 된다"""
        )
        Double pnsLat,

        @Schema(
                examples = "127.09582059",
                description = "보행자 입구점 경도. 장소검색 응답의 `pnsLon`을 그대로 전달한다"
        )
        Double pnsLon
) {
    /** 입구점을 모르는 호출부(조회 응답 등)용. */
    public LocationInfo(String name, String nickname, Double latitude, Double longitude,
                        String roadAddress, Long favoriteId) {
        this(name, nickname, latitude, longitude, roadAddress, favoriteId, null, null);
    }

    /**
     * 경로 탐색에 실제로 쓸 좌표 [위도, 경도].
     *
     * 걸어서 도착하는 모드(`WALK`·`TRANSIT`)만 보행자 입구점을 쓴다. 대중교통도 마지막 구간은 걷는다.
     * 입구점이 없거나 값이 이상하면 중심점으로 폴백한다 — 티맵이 값 없음을 0.0으로 채우는 자리가
     * 있어 null 검사만으로는 부족해 범위까지 본다.
     *
     * **`CAR`는 입구점을 쓰지 않는다.** 주차장 POI의 입구점이 주차장이 아니라 본관 보행자 출입구를
     * 가리킨다(실측 `samples/poi-haengjeong` — 「고등동행정복지센터 주차장」의 입구점이 본관 것과 같고
     * 자기 중심점에서 32.7m). 걸어갈 사람에겐 맞지만 차를 댈 자리로는 틀리다.
     *
     * 전면좌표(`frontLat`/`frontLon`)는 대체재가 아니다 — 차량 진입 기준이라
     * 실측(`samples/poi-pangyo`)에서 입구점과 30~71m 벌어졌다. 아예 받지 않는다.
     */
    @JsonIgnore
    public List<Double> routingCoordinate(TransportMode mode) {
        boolean onFoot = mode != TransportMode.CAR;
        if (onFoot && GeoUtils.isInKorea(pnsLat, pnsLon)) return List.of(pnsLat, pnsLon);
        return List.of(latitude, longitude);
    }
}
