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
import mtvs.onvision.vision.navigation.dto.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "navigation API", description = "길안내 API")
public interface NavigationControllerSupporter {

    @Operation(
            summary = "보행자·자동차 경로 검색",
            description = """
                    피보호자(WARD)·기기(DEVICE) 토큰 모두 가능. `mode`는 `WALK` 또는 `CAR`이고 `TRANSIT`을 보내면 400이다
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
                                                    "address": "서울 강남구 강남대로 지하 476",
                                                    "favoriteId": null
                                                },
                                                "end": {
                                                    "name": "말죽거리공원사거리",
                                                    "nickname": "회사",
                                                    "latitude": 37.479103,
                                                    "longitude": 127.037476,
                                                    "address": "서울 서초구 강남대로 213",
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
                                                    "address": "서울 강남구 강남대로 지하 476",
                                                    "favoriteId": null
                                                },
                                                "end": {
                                                    "name": "말죽거리공원사거리",
                                                    "nickname": "말죽거리공원사거리",
                                                    "latitude": 37.479103,
                                                    "longitude": 127.037476,
                                                    "address": "서울 서초구 강남대로 213",
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
                                                                    "startingAddress": "서울 강남구 강남대로 지하 476",
                                                                    "startingCoordinate": [37.504585, 127.024798],
                                                                    "destinationName": "말죽거리공원사거리",
                                                                    "destinationAddress": "서울 서초구 강남대로 213",
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
                                                                    "startingAddress": "서울 강남구 강남대로 지하 476",
                                                                    "startingCoordinate": [37.504585, 127.024798],
                                                                    "destinationName": "말죽거리공원사거리",
                                                                    "destinationAddress": "서울 서초구 강남대로 213",
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
                    피보호자(WARD)·기기(DEVICE) 토큰 모두 가능. `mode`는 `TRANSIT`이어야 하고 아니면 400이다.

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
                                            "address": "서울 강남구 강남대로 지하 476",
                                            "favoriteId": null
                                        },
                                        "end": {
                                            "name": "말죽거리공원사거리",
                                            "nickname": "말죽거리공원사거리",
                                            "latitude": 37.479103,
                                            "longitude": 127.037476,
                                            "address": "서울 서초구 강남대로 213",
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
                                                                "startingAddress": "서울 강남구 강남대로 지하 476",
                                                                "startingCoordinate": [37.504585, 127.024798],
                                                                "destinationName": "말죽거리공원사거리",
                                                                "destinationAddress": "서울 서초구 강남대로 213",
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
                    피보호자(WARD)·기기(DEVICE) 토큰 모두 가능. 검색으로 Redis에 담아둔 경로를 DB로 옮겨 **안내를 시작**한다.

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
                    피보호자(WARD)·보호자(GUARDIAN)·기기(DEVICE) 토큰 모두 가능. **보호자가 호출하면 피보호자의 경로를 조회한다.**
                    진행 중인 경로는 한 사람당 하나뿐이라 id를 받지 않는다.

                    `report`는 **선택 시점에 저장해둔 경로 원문**이고 `mode`에 따라 모양이 다르다.

                    | mode | report 구조 |
                    |---|---|
                    | `WALK`·`CAR` | `{ summary, startSource, startAccuracy, startRecordedAt, requestedStart, snappedStart, snapDistanceM, requestedEnd, routePath, report: [RouteStep] }` |
                    | `TRANSIT` | `{ summary, legs: [TransitLeg] }` |

                    보행자·자동차의 `report[]`는 **step 하나가 "안내점 + 그 다음 구간"을 통째로 갖는다.**
                    `pathToNext`를 순서대로 이으면 폴리라인이고, `facility`가 `횡단보도`·`계단`인 step의
                    좌표가 마커다.

                    **`routePath`는 그 폴리라인을 서버가 미리 이어둔 것**이다. 경로 형상만 그릴 거면
                    `report[]`를 순회하지 말고 이것만 쓰면 된다. 구간 경계에서 겹치는 좌표는 제거돼 있다.

                    `requestedStart`는 티맵에 보낸 출발 좌표,
                    `snappedStart`는 티맵이 보행로 위로 옮긴 실제 안내 시작 좌표다.
                    `snapDistanceM`은 둘 사이 거리(m)이며 **이 값이 크면 안내가 사용자가 서 있는 자리에서
                    시작하지 않는다는 뜻**이다.

                    `startSource`는 그 출발 좌표가 어디서 왔는지다.

                    | 값 | 뜻 |
                    |---|---|
                    | `REQUEST` | 요청에 실려 온 좌표를 그대로 썼다 |
                    | `SERVER_CACHE` | 요청 좌표가 문턱을 못 넘어 서버에 저장된 최신 위치로 폴백했다 |

                    `startAccuracy`(m)와 `startRecordedAt`은 그 좌표의 반경 오차와 측정 시각이다.
                    `SERVER_CACHE`가 자주 나오면 앱의 위치 보고가 끊기고 있다는 신호다.

                    **둘 다 문턱을 못 넘으면 경로를 만들지 않고 409 `LOW_CONFIDENCE_LOCATION`을 준다.**
                    나쁜 좌표로 만든 경로는 음성 안내와 화면이 어긋나므로, 틀린 경로보다 「위치 확인 중」이 낫다.
                    이때는 위치를 다시 받아 재시도하면 된다 — 요청을 고칠 문제가 아니라 잠시 뒤 풀리는 상태다.

                    판정 기준은 정확도 30m 이하(`0`·음수·누락은 불신), 나이는 요청 좌표 20초 ·
                    저장 좌표는 이동 상태에 따라 정지 90초 / 이동 30초다.

                    `requestedEnd`는 **실제로 경로를 계산한 목적지 좌표**다. `WALK`에서 요청의 `end`에
                    보행자 입구점(`pnsLat`/`pnsLon`)이 실려 있으면 중심점이 아니라 그쪽으로 가므로,
                    요약의 `destinationCoordinate`(장소의 중심점)와 다를 수 있다. **안내 종료 판정은 이 좌표로 한다.**

                    `CAR`는 입구점을 쓰지 않아 `destinationCoordinate`와 항상 같다 — 주차장 POI의 입구점이
                    주차장이 아니라 본관 보행자 출입구를 가리키기 때문이다. **필드는 두 모드 모두 나간다.**

                    위 네 필드는 `TRANSIT`에 없다.

                    대중교통의 `legs[]`는 **도보 leg와 대중교통 leg의 모양이 다르다.**
                    도보는 `steps`에 안내문이 있고 `path`가 비어 있다(`steps[].path`를 이어야 한다).
                    대중교통은 반대로 `path`에 좌표가 있고 `steps`가 비어 있다.
                    **환승 도보에는 `steps`가 없다** — 첫·마지막 도보 leg에만 있다.

                    `legs[].mode`는 여기선 **원문**(`WALK`/`BUS`/`SUBWAY`/…)이다.
                    한글 라벨은 `report.summary.legs[].mode` 쪽이다.

                    모든 좌표는 `[위도, 경도]`다.

                    ### `remainingDistanceM` — 남은 거리(m)

                    `report` 안의 거리·시간이 전부 **저장 시점의 값**인 것과 달리, 이 값만 호출할 때마다
                    피보호자의 마지막 좌표를 경로선 위에 투영해 다시 계산한다. 서버가 진행률을 저장하지 않아서
                    **매 호출이 그 시점의 추정치**다. `GET /api/navigations/map`의 같은 이름 필드와 동일한 값이다.

                    **다음 경우 `null`이다. 값이 0인 것과 구분해야 한다.**

                    | 상황 | 왜 |
                    |---|---|
                    | 피보호자 위치가 없음 | 앱이 꺼져 있거나 마지막 좌표가 30분(Redis TTL)을 넘겼다 |
                    | 경로에서 200m 넘게 벗어남 | 어느 구간에 있는지 특정할 수 없다. 틀린 숫자보다 빈 값을 준다 |

                    `report.summary.totalDistance`와 같은 척도라 **`0 <= remainingDistanceM <= totalDistance`** 다.

                    > **대중교통(`TRANSIT`)은 정확도가 떨어진다.** 도보·자동차는 구간마다 누적 거리가 저장돼 있지만
                    > 대중교통에는 없어서 leg 길이를 이어 붙여 추정한다. 대략적인 값으로만 쓸 것.
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
                                                                        "address": "서울 강남구 강남대로 지하 476",
                                                                        "favoriteId": null
                                                                    },
                                                                    "end": {
                                                                        "name": "말죽거리공원사거리",
                                                                        "nickname": "말죽거리공원사거리",
                                                                        "latitude": 37.479103,
                                                                        "longitude": 127.037476,
                                                                        "address": "서울 서초구 강남대로 213",
                                                                        "favoriteId": null
                                                                    },
                                                                    "remainingDistanceM": 2450,
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
                                                                            "startingAddress": "서울 강남구 강남대로 지하 476",
                                                                            "startingCoordinate": [37.504585, 127.024798],
                                                                            "destinationName": "말죽거리공원사거리",
                                                                            "destinationAddress": "서울 서초구 강남대로 213",
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
                                                                        "address": "서울 강남구 강남대로 지하 476",
                                                                        "favoriteId": null
                                                                    },
                                                                    "end": {
                                                                        "name": "말죽거리공원사거리",
                                                                        "nickname": "말죽거리공원사거리",
                                                                        "latitude": 37.479103,
                                                                        "longitude": 127.037476,
                                                                        "address": "서울 서초구 강남대로 213",
                                                                        "favoriteId": null
                                                                    },
                                                                    "remainingDistanceM": 2450,
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
                                                                            "startingAddress": "서울 강남구 강남대로 지하 476",
                                                                            "startingCoordinate": [37.504585, 127.024798],
                                                                            "destinationName": "말죽거리공원사거리",
                                                                            "destinationAddress": "서울 서초구 강남대로 213",
                                                                            "destinationCoordinate": [37.479103, 127.037476]
                                                                        },
                                                                        "startSource": "REQUEST",
                                                                        "startAccuracy": 8.2,
                                                                        "startRecordedAt": "2026-08-26T07:35:29.900Z",
                                                                        "requestedStart": [37.504600, 127.024750],
                                                                        "snappedStart": [37.504585, 127.024798],
                                                                        "snapDistanceM": 4.6,
                                                                        "requestedEnd": [37.479050, 127.037510],
                                                                        "routePath": [
                                                                            [37.504562, 127.024810],
                                                                            [37.504390, 127.024230],
                                                                            [37.503790, 127.024490],
                                                                            [37.503500, 127.024630]
                                                                        ],
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
                    피보호자(WARD)·기기(DEVICE) 토큰 모두 가능. 진행 중인 경로의 상태를 `COMPLETED`로 바꾼다.
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
                    피보호자(WARD)·기기(DEVICE) 토큰 모두 가능. 진행 중인 경로의 상태를 `CANCELED`로 바꾼다.

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
                    피보호자(WARD)·보호자(GUARDIAN) 토큰 모두 가능. **보호자가 호출하면 피보호자의 경로를 조회한다.**
                    기기(DEVICE)는 막혀 있다 — Quest는 턴바이턴이 필요해 `/processing`을 쓴다.

                    보호자 화면의 지도에 목적지와 경로선을 그리기 위한 조회 API.
                    피보호자 앱도 활성 경로 감지에 이 API를 쓴다.

                    `/processing`과 달리 턴바이턴 안내 정보를 빼고 **지도에 필요한 값만** 내보낸다.
                    `path`는 구간별 좌표를 하나로 이어붙인 것이며 `{latitude, longitude}` 객체 배열이다.

                    **접두사 없는 `name`·`address`·`latitude`·`longitude`는 목적지다.**
                    출발지는 `departureName`·`departureAddress`·`departureLatitude`·`departureLongitude`로 따로 나간다.

                    **출발 좌표는 `path`의 첫 점과 항상 같다.** 경로가 실제로 시작한 지점이라서다 —
                    서버는 요청에 실려 온 출발 좌표를 그대로 쓰지 않고, 못 믿으면 저장된 최신 위치로 폴백한다.
                    반면 `departureName`·`departureAddress`는 사용자가 고른 출발지의 이름이라 그 좌표의 주소가 아니다.

                    목적지 좌표는 티맵에 보낸 값(보행자 입구점일 수 있다)과 다를 수 있다.

                    **진행 중인 경로가 없으면 404가 아니라 `data: null`이다.** 목적지 미설정은 오류가 아니다.

                    `distanceM`·`etaMin`은 **경로를 저장한 시점의 전체 값**이다. 이동한 만큼 줄어들지 않는다.
                    줄어드는 값은 `remainingDistanceM` 하나뿐이다.

                    ### `remainingDistanceM` — 남은 거리(m)

                    호출할 때마다 피보호자의 마지막 좌표를 경로선 위에 투영해 다시 계산한다.
                    서버가 진행률을 저장하지 않기 때문에 **매 호출이 그 시점의 추정치**다.

                    **다음 경우 `null`이다. 값이 0인 것과 구분해야 한다.**

                    | 상황 | 왜 |
                    |---|---|
                    | 피보호자 위치가 없음 | 앱이 꺼져 있거나 마지막 좌표가 30분(Redis TTL)을 넘겼다 |
                    | 경로에서 200m 넘게 벗어남 | 어느 구간에 있는지 특정할 수 없다. 틀린 숫자보다 빈 값을 준다 |

                    `distanceM`과 같은 척도로 맞춰서 내보내므로 **`0 <= remainingDistanceM <= distanceM`** 이 보장된다.

                    > **대중교통(`TRANSIT`)은 정확도가 떨어진다.** 도보·자동차는 구간마다 누적 거리가 저장돼 있지만
                    > 대중교통에는 없어서 leg 길이를 이어 붙여 추정한다. 게다가 티맵이 주는 `totalDistance`가
                    > leg 길이 합과 일치하지 않아 비율로 환산한다. 대략적인 값으로만 쓸 것.""",
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
                                                                    "departureName": "회사",
                                                                    "departureAddress": "서울 강남구 강남대로 지하 476",
                                                                    "departureLatitude": 37.504585,
                                                                    "departureLongitude": 127.024798,
                                                                    "distanceM": 24269,
                                                                    "remainingDistanceM": 2450,
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
                                                    name = "위치를 알 수 없을 때 — remainingDistanceM만 null",
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
                                                                    "departureName": "회사",
                                                                    "departureAddress": "서울 강남구 강남대로 지하 476",
                                                                    "departureLatitude": 37.504585,
                                                                    "departureLongitude": 127.024798,
                                                                    "distanceM": 24269,
                                                                    "remainingDistanceM": null,
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
                    description = "기기(DEVICE) 토큰으로 실행했을때",
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
                    description = "보호자가 호출했는데 연결된 피보호자가 없을때. 피보호자 호출에서는 발생하지 않는다",
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

    @Operation(
            summary = "최근 일주일 경로 조회",
            description = """
                    피보호자가 최근 일주일 동안 만든 경로를 최신순으로 준다. 피보호자·보호자 모두 호출할 수 있고,
                    보호자가 부르면 연결된 피보호자의 경로가 나온다.

                    기준은 **6일 전 00:00(KST) 이후**이며 오늘을 포함해 7일이다. `GET /api/alerts/lastweek`과 같은 규칙이다.
                    건수 상한과 페이지 파라미터는 없다.

                    **상태로 거르지 않는다** — 진행 중(`IN_PROGRESS`)·완료(`COMPLETED`)·취소(`CANCELED`)가 모두 섞여 나온다.
                    구분이 필요하면 `status`를 보고 화면에서 처리한다.

                    `createdAt`은 **경로를 만든 시각**(출발 시각)이지 도착 시각이 아니다. 서버에 완료 시각 컬럼이 없다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "8"))
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
                                                    name = "경로가 있을때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "ROUTE_READ",
                                                                "message": "경로가 정상적으로 조회되었습니다.",
                                                                "data": [
                                                                    {
                                                                        "id": 12,
                                                                        "destinationName": "회사",
                                                                        "createdAt": "2026-08-11T09:14:03",
                                                                        "status": "IN_PROGRESS"
                                                                    },
                                                                    {
                                                                        "id": 11,
                                                                        "destinationName": "신논현역",
                                                                        "createdAt": "2026-08-10T18:02:41",
                                                                        "status": "COMPLETED"
                                                                    },
                                                                    {
                                                                        "id": 10,
                                                                        "destinationName": "말죽거리공원사거리",
                                                                        "createdAt": "2026-08-09T11:30:12",
                                                                        "status": "CANCELED"
                                                                    }
                                                                ]
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "경로가 없을때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "ROUTE_READ",
                                                                "message": "경로가 정상적으로 조회되었습니다.",
                                                                "data": []
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "보호자가 호출했는데 연결된 피보호자가 없을때",
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
    ResponseEntity<ApiResult<List<RouteSummary>>> getRoutesInWeek(CurrentUser currentUser);
}
