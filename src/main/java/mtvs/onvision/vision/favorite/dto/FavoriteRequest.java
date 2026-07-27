package mtvs.onvision.vision.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FavoriteRequest(
        @NotBlank
        @Size(max = 50)
        @Schema(
                examples = "287479301",
                description = "티맵 장소 고유키. 장소검색 응답의 pkey를 그대로 전달. 중복 저장 판별 기준",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String pkey,

        @NotBlank
        @Size(max = 50)
        @Schema(
                examples = "화목순대국 광화문1호점",
                description = "지도상의 장소명. 장소검색 응답의 name을 그대로 전달",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String name,

        @Size(max = 50)
        @Schema(
                examples = "점심집",
                description = "사용자가 붙이는 별칭. 생략 가능. 목록 조회시 별칭 있는 항목이 먼저 정렬된다"
        )
        String nickname,

        @NotNull
        @Schema(
                examples = "37.57120358",
                description = "위도. 장소검색 응답의 noorLat을 그대로 전달",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Double noorLat,

        @NotNull
        @Schema(
                examples = "126.97471568",
                description = "경도. 장소검색 응답의 noorLon을 그대로 전달",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Double noorLon,

        @NotBlank
        @Size(max = 100)
        @Schema(
                examples = "서울 종로구 당주동 40",
                description = "지번 주소. 장소검색 응답의 landAddress를 그대로 전달",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String landAddress,

        @NotBlank
        @Size(max = 100)
        @Schema(
                examples = "서울 종로구 새문안로5길 11",
                description = "도로명 주소. 장소검색 응답의 roadAddress를 그대로 전달",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String roadAddress
) {
}
