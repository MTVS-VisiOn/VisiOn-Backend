package mtvs.onvision.vision.navigation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.time.Instant;

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
    @Valid
    @Schema(
            description = "출발지. 클라이언트가 좌표까지 실어 보낸다",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    LocationInfo start,

    @NotNull
    @Valid
    @Schema(
            description = "도착지",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    LocationInfo end,

    @Schema(
            examples = "38b4ef33-9724-42f5-95ca-b7bd021ef889",
            description = """
                    출발 좌표를 만든 GPS 샘플의 식별자. 모바일이 측정할 때 생성해
                    BLE(→Quest)와 `POST /api/locations` 양쪽에 같은 값을 싣는다.

                    같은 샘플이 다시 오면 재측정이 아니라 **재전송**이라는 뜻이다.
                    측정 시각만으로는 재전송을 구분할 수 없어(앱이 전송 시각으로 다시 찍는다) 이 값이 필요하다.

                    없어도 요청은 처리된다 — 그때는 좌표 동일성으로 대신 판정한다"""
    )
    String startSampleId,

    @Schema(
            examples = "8.2",
            description = """
                    출발 좌표의 반경 오차(m).

                    **30m를 넘거나 0 이하이거나 비어 있으면 이 좌표를 쓰지 않는다.**
                    서버에 저장된 최신 위치로 폴백하고, 그것도 못 넘으면 409 `LOW_CONFIDENCE_LOCATION`이다.

                    `0`과 음수는 「정확도 없음」이지 「오차 0」이 아니므로 전부 불신 처리한다"""
    )
    Float startAccuracy,

    @Schema(
            examples = "2026-08-26T07:35:29.900Z",
            description = """
                    출발 좌표를 **실제로 측정한** 시각. 전송 시각이 아니다.

                    재전송할 때도 원래 측정 시각을 유지해야 한다 — 갱신하면 서버가
                    오래된 좌표를 최신으로 오인한다.

                    20초를 넘으면 이 좌표를 쓰지 않는다. 서버 시각보다 미래면 5초까지는
                    시계 오차로 보고 0초 취급하며, 그 이상은 불신한다"""
    )
    Instant startRecordedAt
) {

    /** 측정 메타가 없는 호출부(레거시 클라이언트·테스트 픽스처)용. */
    public NavigationPreRequest(TransportMode mode, LocationInfo start, LocationInfo end) {
        this(mode, start, end, null, null, null);
    }
}
