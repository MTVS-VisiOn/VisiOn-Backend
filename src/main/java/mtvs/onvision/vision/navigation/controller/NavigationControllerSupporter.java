package mtvs.onvision.vision.navigation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.navigation.dto.MapResponse;
import mtvs.onvision.vision.navigation.dto.NavigationPreRequest;
import mtvs.onvision.vision.navigation.dto.NavigationResponse;
import mtvs.onvision.vision.navigation.dto.NavigationSummary;
import mtvs.onvision.vision.navigation.dto.RouteRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "navigation API", description = "길안내 API")
public interface NavigationControllerSupporter {

    @Operation(
            summary = "보행자·자동차 경로 검색",
            description = """
                    피보호자만 가능. `mode`는 `WALK` 또는 `CAR`이고 `TRANSIT`을 보내면 400이다
                    (대중교통은 `POST /api/navigations/search/transit`).

                    응답은 **요약만** 나간다. 시각장애인 대상이라 지도를 실을 이유가 없고,
                    "총 25분, 횡단보도 44개, 육교 1곳"으로 이 경로로 갈지 판단하게 한다.
                    상세 경로(step 목록·폴리라인)는 Redis에 **30분** 보관하고,
                    경로를 선택(`POST /api/navigations`)할 때 DB로 넘어간다.

                    `mode`마다 Redis 키가 갈린다. `WALK`로 찾고 `CAR`로 다시 찾으면 둘 다 남는다.

                    좌표는 `[위도, 경도]` 순서다(티맵 원본은 `[경도, 위도]`이고 경계에서 뒤집는다).
                    `nickname`이 있으면 `startingName`·`destinationName`에 그 값이, 없으면 `name`이 들어간다.

                    응답 필드는 `mode`에 따라 다르다 — `WALK`는 `crosswalkCount`·`stairsCount`·
                    `overpassCount`·`underpassCount`, `CAR`는 `totalFare`(통행료)·`taxiFare`(택시 예상요금).
                    섞이지 않는다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = {
                            @ExampleObject(
                                    name = "보행자 (WALK) — 즐겨찾기에서 고른 도착지",
                                    value = """
                                            {
                                                "mode": "WALK",
                                                "start": {
                                                    "name": "신논현역",
                                                    "nickname": "신논현역",
                                                    "latitude": 37.504585,
                                                    "longitude": 127.024798,
                                                    "roadAddress": "서울 강남구 강남대로 지하 476",
                                                    "favoriteId": null
                                                },
                                                "end": {
                                                    "name": "말죽거리공원사거리",
                                                    "nickname": "회사",
                                                    "latitude": 37.479103,
                                                    "longitude": 127.037476,
                                                    "roadAddress": "서울 서초구 강남대로 213",
                                                    "favoriteId": 3
                                                }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "자동차 (CAR)",
                                    value = """
                                            {
                                                "mode": "CAR",
                                                "start": {
                                                    "name": "신논현역",
                                                    "nickname": "신논현역",
                                                    "latitude": 37.504585,
                                                    "longitude": 127.024798,
                                                    "roadAddress": "서울 강남구 강남대로 지하 476",
                                                    "favoriteId": null
                                                },
                                                "end": {
                                                    "name": "말죽거리공원사거리",
                                                    "nickname": "말죽거리공원사거리",
                                                    "latitude": 37.479103,
                                                    "longitude": 127.037476,
                                                    "roadAddress": "서울 서초구 강남대로 213",
                                                    "favoriteId": null
                                                }
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "보행자 (WALK)",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "NAVIGATION_SEARCH",
                                                                "message": "경로가 성공적으로 검색되었습니다.",
                                                                "data": {
                                                                    "index": 0,
                                                                    "mode": "WALK",
                                                                    "totalDistance": 24269,
                                                                    "totalTime": 21600,
                                                                    "crosswalkCount": 44,
                                                                    "stairsCount": 0,
                                                                    "overpassCount": 1,
                                                                    "underpassCount": 0,
                                                                    "startingName": "신논현역",
                                                                    "startingRoadAddress": "서울 강남구 강남대로 지하 476",
                                                                    "startingCoordinate": [37.504585, 127.024798],
                                                                    "destinationName": "말죽거리공원사거리",
                                                                    "destinationRoadAddress": "서울 서초구 강남대로 213",
                                                                    "destinationCoordinate": [37.479103, 127.037476]
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "자동차 (CAR)",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "NAVIGATION_SEARCH",
                                                                "message": "경로가 성공적으로 검색되었습니다.",
                                                                "data": {
                                                                    "index": 0,
                                                                    "mode": "CAR",
                                                                    "totalDistance": 22536,
                                                                    "totalTime": 1879,
                                                                    "totalFare": 1000,
                                                                    "taxiFare": 22200,
                                                                    "startingName": "신논현역",
                                                                    "startingRoadAddress": "서울 강남구 강남대로 지하 476",
                                                                    "startingCoordinate": [37.504585, 127.024798],
                                                                    "destinationName": "말죽거리공원사거리",
                                                                    "destinationRoadAddress": "서울 서초구 강남대로 213",
                                                                    "destinationCoordinate": [37.479103, 127.037476]
                                                                }
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "필수값이 비었거나, mode가 TRANSIT이거나, 티맵이 장소를 거부했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "mode가 TRANSIT",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "INVALID_TRANSFER",
                                                                "message": "잘못된 이동수단이 입력되었습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "필수값 누락",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "VALIDATION_FAILED",
                                                                "message": "널이어서는 안됩니다",
                                                                "data": null
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "서비스 지역이 아니거나 좌표 형식 오류",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "INVALID_LOCATION",
                                                                "message": "잘못된 장소가 입력되었습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "티맵이 경로를 못 찾았을때. 직선거리가 너무 멀면 보행자 경로가 거부된다",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_TMAP_ROUTE",
                                                        "message": "티맵에서 경로를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "티맵 호출 자체가 실패했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "TMAP_API_ERROR",
                                                        "message": "지도 서비스 호출에 실패했습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<NavigationSummary>> searchNavigation(NavigationPreRequest request,
                                                                 CurrentUser currentUser);

    @Operation(
            summary = "대중교통 경로 검색",
            description = """
                    피보호자만 가능. `mode`는 `TRANSIT`이어야 하고 아니면 400이다.

                    **후보를 여러 개 준다.** 보행자·자동차와 달리 사용자가 골라야 하므로
                    `index`(0부터)가 붙어 나가고, 선택할 때 그 값을 `POST /api/navigations`에 보낸다.
                    후보 전체는 Redis에 **30분** 보관한다.

                    **운행이 끝난 노선(`service: 0`)이 하나라도 섞인 후보는 목록에서 뺀다.**
                    티맵은 시각을 반영하지 않아 새벽 2시 30분에도 지하철을 추천한다.
                    필터 후 후보가 0개면 404 `NOT_IN_SERVICE`.

                    정렬은 **환승 적은 순 → 도보 짧은 순**이다(티맵은 정렬을 안 해준다).

                    `legs[].distance`의 합은 `totalDistance`와 **일치하지 않는다.**
                    총계는 티맵 값을 그대로 쓰므로 합산 검산을 하면 안 된다.

                    `legs[].mode`는 한글 라벨(`도보`·`버스`·`지하철`·`고속/시외버스`·`기차`·`항공`·`해운`)이다.
                    원문(`WALK`/`BUS`/…)이 필요하면 선택 후 조회 응답의 `report.legs[].mode`에 있다.

                    `stationCount`는 **정거장 수**라 정류장 목록 길이보다 1 작다(승차 정류장을 포함해서 오기 때문).
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "2"))
    )
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(
                            name = "대중교통 (TRANSIT)",
                            value = """
                                    {
                                        "mode": "TRANSIT",
                                        "start": {
                                            "name": "신논현역",
                                            "nickname": "신논현역",
                                            "latitude": 37.504585,
                                            "longitude": 127.024798,
                                            "roadAddress": "서울 강남구 강남대로 지하 476",
                                            "favoriteId": null
                                        },
                                        "end": {
                                            "name": "말죽거리공원사거리",
                                            "nickname": "말죽거리공원사거리",
                                            "latitude": 37.479103,
                                            "longitude": 127.037476,
                                            "roadAddress": "서울 서초구 강남대로 213",
                                            "favoriteId": null
                                        }
                                    }
                                    """
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 성공. 후보는 최대 10개",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "NAVIGATION_SEARCH",
                                                        "message": "경로가 성공적으로 검색되었습니다.",
                                                        "data": [
                                                            {
                                                                "index": 0,
                                                                "mode": "TRANSIT",
                                                                "totalDistance": 3278,
                                                                "totalTime": 953,
                                                                "totalWalkTime": 389,
                                                                "totalWalkDistance": 461,
                                                                "transferCount": 0,
                                                                "totalFare": 3000,
                                                                "legs": [
                                                                    {
                                                                        "mode": "도보",
                                                                        "route": null,
                                                                        "routes": [],
                                                                        "startName": "출발지",
                                                                        "endName": "신논현역.(구)교보타워사거리",
                                                                        "stationCount": null,
                                                                        "sectionTime": 209,
                                                                        "distance": 256
                                                                    },
                                                                    {
                                                                        "mode": "버스",
                                                                        "route": "광역:9711",
                                                                        "routes": ["광역:9711"],
                                                                        "startName": "신논현역.(구)교보타워사거리",
                                                                        "endName": "교육개발원입구",
                                                                        "stationCount": 6,
                                                                        "sectionTime": 564,
                                                                        "distance": 2955
                                                                    },
                                                                    {
                                                                        "mode": "도보",
                                                                        "route": null,
                                                                        "routes": [],
                                                                        "startName": "교육개발원입구",
                                                                        "endName": "도착지",
                                                                        "stationCount": null,
                                                                        "sectionTime": 180,
                                                                        "distance": 205
                                                                    }
                                                                ],
                                                                "startingName": "신논현역",
                                                                "startingRoadAddress": "서울 강남구 강남대로 지하 476",
                                                                "startingCoordinate": [37.504585, 127.024798],
                                                                "destinationName": "말죽거리공원사거리",
                                                                "destinationRoadAddress": "서울 서초구 강남대로 213",
                                                                "destinationCoordinate": [37.479103, 127.037476]
                                                            }
                                                        ]
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "mode가 TRANSIT이 아니거나, 출발·도착이 너무 가깝거나 서비스 지역이 아닐때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "mode가 TRANSIT이 아님",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "INVALID_TRANSFER",
                                                                "message": "잘못된 이동수단이 입력되었습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "서비스 지역이 아님",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "INVALID_LOCATION",
                                                                "message": "잘못된 장소가 입력되었습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "경로가 없거나, 있어도 전부 운행이 끝났을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "운행 중인 노선이 없음 (새벽)",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "NOT_IN_SERVICE",
                                                                "message": "지금은 운행 중인 대중교통이 없습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "경로 없음",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "NOT_FOUND_TMAP_ROUTE",
                                                                "message": "티맵에서 경로를 찾을 수 없습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "티맵 호출 실패 또는 일일 호출 한도 초과",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "TMAP_API_ERROR",
                                                        "message": "지도 서비스 호출에 실패했습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<List<NavigationSummary>>> searchNavigationTransit(NavigationPreRequest request,
                                                                              CurrentUser currentUser);

    @Operation(
            summary = "경로 선택",
            description = """
                    피보호자만 가능. 검색으로 Redis에 담아둔 경로를 DB로 옮겨 **안내를 시작**한다.

                    `mode`는 어느 검색 결과를 고르는지를 가리킨다. 검색 때 쓴 값과 같아야 한다 —
                    `mode`마다 Redis 키가 따로라 `WALK`로 찾아놓고 `CAR`로 선택하면 다른 경로가 저장되거나
                    404가 난다.

                    `index`는 **대중교통에만** 필요하다. 검색 응답의 `index`를 그대로 보낸다.
                    `WALK`·`CAR`는 후보가 하나뿐이라 없어도 된다.

                    **진행 중인 경로가 이미 있으면 그것을 취소하고 새로 만든다.** 한 사람에게
                    진행 중인 경로는 하나뿐이다.

                    검색한 지 **30분이 지나면** Redis에서 사라져 404가 난다. 다시 검색해야 한다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "3"))
    )
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = {
                            @ExampleObject(
                                    name = "대중교통 — 첫 번째 후보를 고름",
                                    value = """
                                            {
                                                "mode": "TRANSIT",
                                                "index": 0
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "보행자·자동차 — index 불필요",
                                    value = """
                                            {
                                                "mode": "WALK"
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "저장 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "ROUTE_CREATED",
                                                        "message": "경로가 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "mode가 비었거나, 대중교통인데 index가 없을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "INVALID_TRANSIT_INDEX",
                                                        "message": "대중교통 경로의 인덱스가 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "검색 결과가 만료됐거나(30분), 보낸 index가 후보에 없을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_ROUTE",
                                                        "message": "경로를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> saveRoute(RouteRequest request,
                                             CurrentUser currentUser);

    @Operation(
            summary = "진행 중인 경로 조회",
            description = """
                    피보호자와 보호자 모두 가능. **보호자가 호출하면 피보호자의 경로를 조회한다.**
                    진행 중인 경로는 한 사람당 하나뿐이라 id를 받지 않는다.

                    `report`는 **선택 시점에 저장해둔 경로 원문**이고 `mode`에 따라 모양이 다르다.

                    | mode | report 구조 |
                    |---|---|
                    | `WALK`·`CAR` | `{ summary, report: [RouteStep] }` |
                    | `TRANSIT` | `{ summary, legs: [TransitLeg] }` |

                    보행자·자동차의 `report[]`는 **step 하나가 "안내점 + 그 다음 구간"을 통째로 갖는다.**
                    `pathToNext`를 순서대로 이으면 폴리라인이고, `facility`가 `횡단보도`·`계단`인 step의
                    좌표가 마커다.

                    대중교통의 `legs[]`는 **도보 leg와 대중교통 leg의 모양이 다르다.**
                    도보는 `steps`에 안내문이 있고 `path`가 비어 있다(`steps[].path`를 이어야 한다).
                    대중교통은 반대로 `path`에 좌표가 있고 `steps`가 비어 있다.
                    **환승 도보에는 `steps`가 없다** — 첫·마지막 도보 leg에만 있다.

                    `legs[].mode`는 여기선 **원문**(`WALK`/`BUS`/`SUBWAY`/…)이다.
                    한글 라벨은 `report.summary.legs[].mode` 쪽이다.

                    모든 좌표는 `[위도, 경도]`다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "4"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "대중교통 (TRANSIT) — legs 일부 생략",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "ROUTE_READ",
                                                                "message": "경로가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "id": 2,
                                                                    "mode": "TRANSIT",
                                                                    "start": {
                                                                        "name": "신논현역",
                                                                        "nickname": "신논현역",
                                                                        "latitude": 37.504585,
                                                                        "longitude": 127.024798,
                                                                        "roadAddress": "서울 강남구 강남대로 지하 476",
                                                                        "favoriteId": null
                                                                    },
                                                                    "end": {
                                                                        "name": "말죽거리공원사거리",
                                                                        "nickname": "말죽거리공원사거리",
                                                                        "latitude": 37.479103,
                                                                        "longitude": 127.037476,
                                                                        "roadAddress": "서울 서초구 강남대로 213",
                                                                        "favoriteId": null
                                                                    },
                                                                    "report": {
                                                                        "summary": {
                                                                            "index": 0,
                                                                            "mode": "TRANSIT",
                                                                            "totalDistance": 3278,
                                                                            "totalTime": 953,
                                                                            "totalWalkTime": 389,
                                                                            "totalWalkDistance": 461,
                                                                            "transferCount": 0,
                                                                            "totalFare": 3000,
                                                                            "legs": [
                                                                                {
                                                                                    "mode": "버스",
                                                                                    "route": "광역:9711",
                                                                                    "routes": ["광역:9711"],
                                                                                    "startName": "신논현역.(구)교보타워사거리",
                                                                                    "endName": "교육개발원입구",
                                                                                    "stationCount": 6,
                                                                                    "sectionTime": 564,
                                                                                    "distance": 2955
                                                                                }
                                                                            ],
                                                                            "startingName": "신논현역",
                                                                            "startingRoadAddress": "서울 강남구 강남대로 지하 476",
                                                                            "startingCoordinate": [37.504585, 127.024798],
                                                                            "destinationName": "말죽거리공원사거리",
                                                                            "destinationRoadAddress": "서울 서초구 강남대로 213",
                                                                            "destinationCoordinate": [37.479103, 127.037476]
                                                                        },
                                                                        "legs": [
                                                                            {
                                                                                "sequence": 0,
                                                                                "mode": "WALK",
                                                                                "route": null,
                                                                                "routeId": null,
                                                                                "type": null,
                                                                                "routeColor": null,
                                                                                "routes": [],
                                                                                "sectionTime": 209,
                                                                                "distance": 256,
                                                                                "routePayment": null,
                                                                                "startName": "출발지",
                                                                                "startCoordinate": [37.504585, 127.024798],
                                                                                "endName": "신논현역.(구)교보타워사거리",
                                                                                "endCoordinate": [37.502713, 127.025077],
                                                                                "stations": [],
                                                                                "steps": [
                                                                                    {
                                                                                        "sequence": 0,
                                                                                        "description": "55m 이동",
                                                                                        "distance": 55,
                                                                                        "streetName": "",
                                                                                        "path": [[37.504562, 127.024810], [37.504390, 127.024230]]
                                                                                    }
                                                                                ],
                                                                                "path": []
                                                                            },
                                                                            {
                                                                                "sequence": 1,
                                                                                "mode": "BUS",
                                                                                "route": "광역:9711",
                                                                                "routeId": "1021340001",
                                                                                "type": 14,
                                                                                "routeColor": "FF3300",
                                                                                "routes": ["광역:9711"],
                                                                                "sectionTime": 564,
                                                                                "distance": 2955,
                                                                                "routePayment": null,
                                                                                "startName": "신논현역.(구)교보타워사거리",
                                                                                "startCoordinate": [37.502713, 127.025077],
                                                                                "endName": "교육개발원입구",
                                                                                "endCoordinate": [37.478555, 127.038541],
                                                                                "stations": [
                                                                                    {
                                                                                        "index": 0,
                                                                                        "stationId": "772647",
                                                                                        "stationName": "신논현역.(구)교보타워사거리",
                                                                                        "coordinate": [37.502714, 127.025078]
                                                                                    }
                                                                                ],
                                                                                "steps": [],
                                                                                "path": [[37.502742, 127.025161], [37.500086, 127.026481]]
                                                                            }
                                                                        ]
                                                                    }
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "보행자 (WALK) — report 일부 생략",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "ROUTE_READ",
                                                                "message": "경로가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "id": 1,
                                                                    "mode": "WALK",
                                                                    "start": {
                                                                        "name": "신논현역",
                                                                        "nickname": "신논현역",
                                                                        "latitude": 37.504585,
                                                                        "longitude": 127.024798,
                                                                        "roadAddress": "서울 강남구 강남대로 지하 476",
                                                                        "favoriteId": null
                                                                    },
                                                                    "end": {
                                                                        "name": "말죽거리공원사거리",
                                                                        "nickname": "말죽거리공원사거리",
                                                                        "latitude": 37.479103,
                                                                        "longitude": 127.037476,
                                                                        "roadAddress": "서울 서초구 강남대로 213",
                                                                        "favoriteId": null
                                                                    },
                                                                    "report": {
                                                                        "summary": {
                                                                            "index": 0,
                                                                            "mode": "WALK",
                                                                            "totalDistance": 3103,
                                                                            "totalTime": 2400,
                                                                            "crosswalkCount": 11,
                                                                            "stairsCount": 0,
                                                                            "overpassCount": 0,
                                                                            "underpassCount": 0,
                                                                            "startingName": "신논현역",
                                                                            "startingRoadAddress": "서울 강남구 강남대로 지하 476",
                                                                            "startingCoordinate": [37.504585, 127.024798],
                                                                            "destinationName": "말죽거리공원사거리",
                                                                            "destinationRoadAddress": "서울 서초구 강남대로 213",
                                                                            "destinationCoordinate": [37.479103, 127.037476]
                                                                        },
                                                                        "report": [
                                                                            {
                                                                                "sequence": 0,
                                                                                "latitude": 37.504585,
                                                                                "longitude": 127.024798,
                                                                                "description": "강남대로를 따라 160m 이동",
                                                                                "turnType": 200,
                                                                                "pointType": "출발지",
                                                                                "facility": "일반보행자도로",
                                                                                "distanceToNext": 160,
                                                                                "timeToNext": 118,
                                                                                "cumulativeDistance": 0,
                                                                                "pathToNext": [[37.504562, 127.024810], [37.504390, 127.024230]]
                                                                            },
                                                                            {
                                                                                "sequence": 1,
                                                                                "latitude": 37.503790,
                                                                                "longitude": 127.024490,
                                                                                "description": "횡단보도를 건너세요",
                                                                                "turnType": null,
                                                                                "pointType": "시설 안내점",
                                                                                "facility": "횡단보도",
                                                                                "distanceToNext": 22,
                                                                                "timeToNext": 15,
                                                                                "cumulativeDistance": 160,
                                                                                "pathToNext": [[37.503790, 127.024490], [37.503500, 127.024630]]
                                                                            },
                                                                            {
                                                                                "sequence": 2,
                                                                                "latitude": 37.479103,
                                                                                "longitude": 127.037476,
                                                                                "description": "도착",
                                                                                "turnType": 201,
                                                                                "pointType": "도착지",
                                                                                "facility": null,
                                                                                "distanceToNext": null,
                                                                                "timeToNext": null,
                                                                                "cumulativeDistance": 3103,
                                                                                "pathToNext": []
                                                                            }
                                                                        ]
                                                                    }
                                                                }
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "진행 중인 경로가 없거나, 보호자에게 연결된 피보호자가 없을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "진행 중인 경로 없음",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "NOT_FOUND_ROUTE",
                                                                "message": "경로를 찾을 수 없습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "보호자-피보호자 연결 없음",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "NOT_FOUND_RELATION",
                                                                "message": "해당하는 relation 을 찾을 수 없습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<NavigationResponse>> getProcessingRoute(CurrentUser currentUser);

    @Operation(
            summary = "경로 완료",
            description = """
                    피보호자만 가능. 진행 중인 경로의 상태를 `COMPLETED`로 바꾼다.
                    행을 지우지 않으므로 나중에 이동 기록으로 쓸 수 있다.

                    진행 중인 경로는 하나뿐이라 id를 받지 않는다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "5"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "완료 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "ROUTE_COMPLETED",
                                                        "message": "경로가 정상적으로 완료되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "진행 중인 경로가 없을때 (이미 완료했거나 취소했을때 포함)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_ROUTE",
                                                        "message": "경로를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> completeRoute(CurrentUser currentUser);

    @Operation(
            summary = "경로 취소",
            description = """
                    피보호자만 가능. 진행 중인 경로의 상태를 `CANCELED`로 바꾼다.

                    새 경로를 선택하면 기존 경로가 자동으로 취소되므로, 이 API는
                    **다른 경로를 고르지 않고 그냥 그만둘 때** 쓴다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "6"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "취소 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "ROUTE_CANCELED",
                                                        "message": "경로가 정상적으로 취소되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "진행 중인 경로가 없을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_ROUTE",
                                                        "message": "경로를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> cancelRoute(CurrentUser currentUser);

    @Operation(
            summary = "지도 표시용 경로 조회",
            description = """
                    보호자 화면의 지도에 목적지와 경로선을 그리기 위한 조회 API.

                    `/processing`과 달리 턴바이턴 안내 정보를 빼고 **지도에 필요한 값만** 내보낸다.
                    `path`는 구간별 좌표를 하나로 이어붙인 것이며 `{latitude, longitude}` 객체 배열이다.

                    **진행 중인 경로가 없으면 404가 아니라 `data: null`이다.** 목적지 미설정은 오류가 아니다.

                    거리·시간은 **경로를 저장한 시점의 전체 값**이다. 이동한 만큼 줄어들지 않는다""",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "7"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "진행 중인 경로가 있을 때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "ROUTE_READ",
                                                                "message": "경로가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "name": "말죽거리공원사거리",
                                                                    "address": "서울 서초구 강남대로 213",
                                                                    "latitude": 37.479103,
                                                                    "longitude": 127.037476,
                                                                    "distanceM": 24269,
                                                                    "etaMin": 360,
                                                                    "departureTime": "2026-08-03T07:08:00Z",
                                                                    "mode": "WALK",
                                                                    "path": [
                                                                        { "latitude": 37.504585, "longitude": 127.024798 },
                                                                        { "latitude": 37.503900, "longitude": 127.025200 }
                                                                    ]
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "진행 중인 경로가 없을 때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "ROUTE_READ",
                                                                "message": "경로가 정상적으로 조회되었습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "피보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "보호자-피보호자 관계를 찾을 수 없을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_RELATION",
                                                        "message": "해당하는 relation 을 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<MapResponse>> getMapRoute(CurrentUser currentUser);
}
