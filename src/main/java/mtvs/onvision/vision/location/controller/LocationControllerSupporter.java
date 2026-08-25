package mtvs.onvision.vision.location.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.location.dto.LastLocationResponse;
import mtvs.onvision.vision.location.dto.LocationRequest;
import mtvs.onvision.vision.location.dto.LocationSearchResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name="location API", description = "기기 연결상태 API")
public interface LocationControllerSupporter {

    @Operation(
            summary = "피보호자 실시간 위치 전송",
            description = "피보호자만 가능",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "전송 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "LOCATION_CREATED",
                                                        "message": "실시간 위치가 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식이 잘못되었을떄(경도값이 비었을때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "경도는 필수값입니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을떄",
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
            )
    })
    ResponseEntity<ApiResult<Void>> receiveLocation(LocationRequest request,
                                                    CurrentUser currentUser);

    @Operation(
            summary = "보호자 피보호자 실시간 위치 조회",
            description = "보호자만 가능",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "2"))
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
                                                    name = "최근 위치 있음",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "LOCATION_READ",
                                                                "message": "실시간 위치가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "latitude": 37.501274,
                                                                    "longitude": 127.039585,
                                                                    "address": "서울특별시 강남구 테헤란로 212 멀티캠퍼스",
                                                                    "status": "도보로 이동중",
                                                                    "recordedAt": "2026-08-04T05:32:10.123Z"
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "최근 위치 없음",
                                                    description = "피보호자가 아직 위치를 보내지 않았거나 보관 기한이 지난 경우. 오류가 아니라 200에 data가 null이다",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "LOCATION_READ",
                                                                "message": "실시간 위치가 정상적으로 조회되었습니다.",
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
                    description = "피보호자 권한으로 실행했을떄",
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
            )
    })
    ResponseEntity<ApiResult<LastLocationResponse>> getLastLocation(CurrentUser currentUser);

    @Operation(
            summary = "피보호자 장소 검색",
            description = """
                    피보호자(WARD)와 기기(DEVICE) 토큰 모두 가능. 기기 토큰은 페어링한 피보호자의 id로 발급되므로 어느 쪽으로 불러도 같은 사람의 위치를 본다.

                    검색 중심 좌표는 클라이언트가 보내지 않고, 서버가 해당 피보호자의 최근 위치(30분 이내)를 읽어 쓴다.

                    최근 위치가 있으면 그 좌표를 기준으로 **거리순 정렬**해 검색하고, 사용한 좌표를 `center`에 담아 돌려준다.
                    반경으로 걸러내지는 않으므로(티맵 `radius=0`, 전국) 먼 곳도 결과에 남는다.

                    최근 위치가 없으면 **티맵을 호출하지 않고** `center`가 null인 빈 결과를 200으로 응답한다.
                    오류가 아니므로 상태 코드로는 구분되지 않는다. `center`가 null이면 위치를 몰라 검색하지 않은 것이고,
                    `center`가 있는데 `infos`가 비어 있으면 검색은 했으나 결과가 없는 것이다.

                    ### 좌표가 둘이다

                    | 필드 | 뜻 | 쓰는 곳 |
                    |---|---|---|
                    | `noorLat`/`noorLon` | POI 중심점 | 지도 표시 |
                    | `pnsLat`/`pnsLon` | 보행자 입구점 | **길안내 목적지** |

                    길안내 목적지로 넘길 때는 **`pns`를 우선 쓴다.** 건물 중심점은 벽 안쪽이라
                    안내가 끝나는 자리가 실제 출입구와 12~26m 어긋난다(실측 `samples/poi-pangyo`).

                    `pns`는 티맵 2025-05 추가분이라 **없는 POI가 있고 그때는 null이다.** 그 경우에만 `noor`로 폴백한다.
                    값이 0.0 등으로 오는 건은 서버가 걸러 null로 내린다.

                    입구 개념이 없는 POI(주차장·도로 등)는 두 좌표가 같은 값으로 온다 — 분기 없이 `pns`를 써도 된다.""",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "3"))
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
                                                    name = "결과 있음",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "LOCATION_SEARCH_READ",
                                                                "message": "장소 검색이 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "totalCount": 5,
                                                                    "count": 5,
                                                                    "page": 1,
                                                                    "center": {
                                                                        "latitude": 37.42827795,
                                                                        "longitude": 127.09831958
                                                                    },
                                                                    "infos": [
                                                                        {
                                                                            "id": "374619",
                                                                            "pkey": "37461901",
                                                                            "name": "왕남초등학교",
                                                                            "noorLat": 37.42850015,
                                                                            "noorLon": 127.09801405,
                                                                            "pnsLat": 37.42836000,
                                                                            "pnsLon": 127.09812000,
                                                                            "landAddress": "경기 성남시 수정구 고등동 589",
                                                                            "roadAddress": "경기 성남시 수정구 왕남로 24"
                                                                        },
                                                                        {
                                                                            "id": "1801606",
                                                                            "pkey": "180160601",
                                                                            "name": "왕남초등학교병설유치원",
                                                                            "noorLat": 37.42794467,
                                                                            "noorLon": 127.09904175,
                                                                            "pnsLat": null,
                                                                            "pnsLon": null,
                                                                            "landAddress": "경기 성남시 수정구 고등동 456-1",
                                                                            "roadAddress": "경기 성남시 수정구"
                                                                        },
                                                                        {
                                                                            "id": "7935140",
                                                                            "pkey": "793514001",
                                                                            "name": "지진겸용 임시주거시설 왕남 초등학교",
                                                                            "noorLat": 37.42836128,
                                                                            "noorLon": 127.09843068,
                                                                            "pnsLat": 37.42828000,
                                                                            "pnsLon": 127.09851000,
                                                                            "landAddress": "경기 성남시 수정구 고등동 461",
                                                                            "roadAddress": "경기 성남시 수정구"
                                                                        },
                                                                        {
                                                                            "id": "11776453",
                                                                            "pkey": "1177645301",
                                                                            "name": "지진옥외대피소 왕남초등학교 운동장",
                                                                            "noorLat": 37.42827795,
                                                                            "noorLon": 127.09831958,
                                                                            "pnsLat": 37.42827795,
                                                                            "pnsLon": 127.09831958,
                                                                            "landAddress": "경기 성남시 수정구 고등동 589",
                                                                            "roadAddress": "경기 성남시 수정구 왕남로 24"
                                                                        },
                                                                        {
                                                                            "id": "11971493",
                                                                            "pkey": "1197149301",
                                                                            "name": "화학사고대피소 왕남초등학교 교사동 등 전체",
                                                                            "noorLat": 37.4286668,
                                                                            "noorLon": 127.0984029,
                                                                            "pnsLat": 37.42858000,
                                                                            "pnsLon": 127.09848000,
                                                                            "landAddress": "경기 성남시 수정구 고등동 589",
                                                                            "roadAddress": "경기 성남시 수정구 왕남로 24"
                                                                        }
                                                                    ]
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "현재 위치를 몰라 검색하지 않음",
                                                    value = """
                                                    {
                                                        "success": true,
                                                        "code": "LOCATION_SEARCH_READ",
                                                        "message": "장소 검색이 정상적으로 조회되었습니다.",
                                                        "data": {
                                                            "totalCount": 0,
                                                            "count": 0,
                                                            "page": 0,
                                                            "center": null,
                                                            "infos": []
                                                        }
                                                    }
                                                    """
                                            ),
                                            @ExampleObject(
                                                    name = "검색했으나 결과 없음",
                                                    value = """
                                                    {
                                                        "success": true,
                                                        "code": "LOCATION_SEARCH_READ",
                                                        "message": "장소 검색이 정상적으로 조회되었습니다.",
                                                        "data": {
                                                            "totalCount": 0,
                                                            "count": 0,
                                                            "page": 0,
                                                            "center": {
                                                                "latitude": 37.42827795,
                                                                "longitude": 127.09831958
                                                            },
                                                            "infos": []
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
                    description = "키워드를 넣지 않았을떄",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "REQUESTPARAM_REQUIRED",
                                                        "message": "해당 파라미터는 필수값입니다. :::keyword",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을떄",
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
            )
    })
    ResponseEntity<ApiResult<LocationSearchResponse>> searchLocation(
            @Parameter(description = "검색 키워드", example = "왕남초등학교", required = true)String keyword,
            CurrentUser currentUser);
}
