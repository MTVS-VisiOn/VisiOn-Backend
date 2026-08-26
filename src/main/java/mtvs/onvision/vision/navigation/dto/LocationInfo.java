package mtvs.onvision.vision.navigation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        Long favoriteId
) {
}
