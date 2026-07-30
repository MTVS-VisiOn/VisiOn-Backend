package mtvs.onvision.vision.navigation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import mtvs.onvision.vision.navigation.domain.TransportMode;

public record NavigationPreRequest(
    @NotNull
    @Schema(
            examples = "WALK",
            description = """
                    이동수단. `WALK`(도보) · `CAR`(자동차) · `TRANSIT`(대중교통).

                    엔드포인트마다 받는 값이 다르다 — `/search`는 `WALK`·`CAR`만,
                    `/search/transit`은 `TRANSIT`만 받고 아니면 400 `INVALID_TRANSFER`다.

                    Redis 키도 이 값으로 갈리므로 경로를 선택할 때 같은 값을 보내야 한다""",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    TransportMode mode,

    @NotNull
    @Schema(
            description = "출발지. 클라이언트가 좌표까지 실어 보낸다",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    LocationInfo start,

    @NotNull
    @Schema(
            description = "도착지",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    LocationInfo end
) {

}
