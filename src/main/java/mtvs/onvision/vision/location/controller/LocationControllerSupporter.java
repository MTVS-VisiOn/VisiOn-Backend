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
                                                                    "isConnected": true,
                                                                    "lastAddress": "서울특별시 강남구 테헤란로 212 멀티캠퍼스",
                                                                    "status": "도보로 이동중"
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
            description = "피보호자만 가능",
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
                                                                    "infos": [
                                                                        {
                                                                            "id": "374619",
                                                                            "pkey": "37461901",
                                                                            "name": "왕남초등학교",
                                                                            "noorLat": 37.42850015,
                                                                            "noorLon": 127.09801405,
                                                                            "landAddress": "경기 성남시 수정구 고등동 589",
                                                                            "roadAddress": "경기 성남시 수정구 왕남로 24"
                                                                        },
                                                                        {
                                                                            "id": "1801606",
                                                                            "pkey": "180160601",
                                                                            "name": "왕남초등학교병설유치원",
                                                                            "noorLat": 37.42794467,
                                                                            "noorLon": 127.09904175,
                                                                            "landAddress": "경기 성남시 수정구 고등동 456-1",
                                                                            "roadAddress": "경기 성남시 수정구"
                                                                        },
                                                                        {
                                                                            "id": "7935140",
                                                                            "pkey": "793514001",
                                                                            "name": "지진겸용 임시주거시설 왕남 초등학교",
                                                                            "noorLat": 37.42836128,
                                                                            "noorLon": 127.09843068,
                                                                            "landAddress": "경기 성남시 수정구 고등동 461",
                                                                            "roadAddress": "경기 성남시 수정구"
                                                                        },
                                                                        {
                                                                            "id": "11776453",
                                                                            "pkey": "1177645301",
                                                                            "name": "지진옥외대피소 왕남초등학교 운동장",
                                                                            "noorLat": 37.42827795,
                                                                            "noorLon": 127.09831958,
                                                                            "landAddress": "경기 성남시 수정구 고등동 589",
                                                                            "roadAddress": "경기 성남시 수정구 왕남로 24"
                                                                        },
                                                                        {
                                                                            "id": "11971493",
                                                                            "pkey": "1197149301",
                                                                            "name": "화학사고대피소 왕남초등학교 교사동 등 전체",
                                                                            "noorLat": 37.4286668,
                                                                            "noorLon": 127.0984029,
                                                                            "landAddress": "경기 성남시 수정구 고등동 589",
                                                                            "roadAddress": "경기 성남시 수정구 왕남로 24"
                                                                        }
                                                                    ]
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "결과 없음",
                                                    value = """
                                                    {
                                                        "success": true,
                                                        "code": "LOCATION_SEARCH_READ",
                                                        "message": "장소 검색이 정상적으로 조회되었습니다.",
                                                        "data": {
                                                            "totalCount": 0,
                                                            "count": 0,
                                                            "page": 0,
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
            @Parameter(description = "검색 키워드", example = "왕남초등학교", required = true)String keyword);
}
