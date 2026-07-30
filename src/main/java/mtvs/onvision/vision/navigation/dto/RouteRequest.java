package mtvs.onvision.vision.navigation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import mtvs.onvision.vision.navigation.domain.TransportMode;

public record RouteRequest(
        @NotNull
        @Schema(
                examples = "TRANSIT",
                description = """
                        어느 검색 결과를 고르는지. **검색 때 보낸 값과 같아야 한다.**

                        `mode`마다 Redis 키가 따로라 `WALK`로 찾아놓고 `CAR`로 선택하면
                        다른 경로가 저장되거나 404 `NOT_FOUND_ROUTE`가 난다""",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        TransportMode mode,

        @Schema(
                examples = "0",
                description = """
                        고른 후보의 순번. 검색 응답의 `index`를 그대로 보낸다.

                        **대중교통에만 필요하다** — `WALK`·`CAR`는 후보가 하나뿐이라 없어도 된다.
                        `TRANSIT`인데 비어 있으면 400 `INVALID_TRANSIT_INDEX`다.

                        `0`은 유효한 값이다"""
        )
        Integer index
) {
}
