package mtvs.onvision.vision.alert.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.alert.dto.AlertResponse;
import mtvs.onvision.vision.alert.dto.ObstacleRequest;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 요청의 {@code occurredAt}은 UTC({@code Z})로 받고, 응답의 모든 시각은 KST(Asia/Seoul)로 내려간다.
 */
@Tag(name = "Alert API", description = "위험 알림 API. 요청 시각은 UTC(Z), 응답 시각은 모두 KST(Asia/Seoul) 기준이다")
public interface AlertControllerSupporter {

    @Operation(
            summary = "장애물 감지 전송",
            description = """
                    피보호자 기기(Quest)가 장애물을 감지했을 때 호출한다. 피보호자만 가능.

                    `multipart/form-data`로 두 파트를 보낸다.
                    - `request` : 감지 정보 JSON. **Content-Type을 `application/json`으로 지정해야 한다.** 지정하지 않으면 415가 난다
                    - `image` : 감지 시점 캡처 이미지

                    저장이 끝나면 보호자에게 푸시 알림이 비동기로 발송된다.
                    알림 발송 실패는 이 API의 응답에 영향을 주지 않는다(저장은 성공으로 응답한다).

                    `occurredAt`은 ISO-8601 UTC(`Z`)로 보낸다. 예) `2026-08-05T09:12:33.512Z`
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = ObstacleDetectMultipart.class),
                    encoding = {
                            @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE),
                            @Encoding(name = "image", contentType = "image/jpeg")
                    }
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "감지 정보 저장 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "DETECT_OBSTACLE_CREATED",
                                                        "message": "장애물 감지가 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식이 잘못되었을 때(좌표나 감지 시각이 비었을 때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "위도는 필수값입니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을 때",
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
                    description = "피보호자에게 연결된 보호자가 없을 때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_RELATION",
                                                        "message": "보호자와 피보호자의 관계를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @ApiUnauthorized
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<Void>> detectObstacle(ObstacleRequest request,
                                                   MultipartFile image,
                                                   CurrentUser currentUser);

    @Operation(
            summary = "알림 상세 조회",
            description = """
                    푸시 알림을 탭했을 때 여는 상세 화면용. 보호자만 가능.

                    푸시 payload의 `data.alertId`를 그대로 경로에 넣는다.
                    자기 피보호자의 알림만 조회할 수 있고, 다른 피보호자의 알림이면 403이 난다.

                    `presignedUrl`은 **조회 시점에 발급되는 임시 URL**이다. 만료되므로 저장해 두지 말고
                    화면을 열 때마다 이 API로 다시 받는다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "2"))
    )
    @Parameter(name = "alertId", description = "알림 id. 푸시 payload의 data.alertId", example = "10", required = true)
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "ALERT_READ",
                                                        "message": "알림 내용이 정상적으로 조회되었습니다.",
                                                        "data": {
                                                            "type": "OBSTACLE",
                                                            "occurredAt": "2026-08-05T18:55:00",
                                                            "occurredPlace": "서울특별시 강남구 테헤란로 152",
                                                            "presignedUrl": "https://onvision.s3.ap-northeast-2.amazonaws.com/alerts/OBSTACLE/2026/08/05/.../obstacle.jpg?X-Amz-Signature=...",
                                                            "content": "전방 2m에 자전거가 세워져 있습니다",
                                                            "action": "위험 음성 재생"
                                                        }
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "피보호자 권한으로 실행했을 때(ACCESS_DENIED) / 다른 피보호자의 알림을 조회했을 때(NOT_GUARDIAN)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "ACCESS_DENIED",
                                                    description = "보호자 권한이 아님",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "ACCESS_DENIED",
                                                                "message": "권한이 없습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "NOT_GUARDIAN",
                                                    description = "보호자이지만 해당 알림의 피보호자와 연결돼 있지 않음",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "NOT_GUARDIAN",
                                                                "message": "해당 피보호자의 보호자가 아닙니다.",
                                                                "data": null
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "알림이 없거나(NOT_FOUND_ALERT) 연결된 피보호자가 없을 때(NOT_FOUND_RELATION)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "NOT_FOUND_ALERT",
                                                    description = "해당 id의 알림이 없음",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "NOT_FOUND_ALERT",
                                                                "message": "알림를 찾을 수 없습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "NOT_FOUND_RELATION",
                                                    description = "이 보호자에게 연결된 피보호자가 없음",
                                                    value = """
                                                            {
                                                                "success": false,
                                                                "code": "NOT_FOUND_RELATION",
                                                                "message": "보호자와 피보호자의 관계를 찾을 수 없습니다.",
                                                                "data": null
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            )
    })
    @ApiUnauthorized
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<AlertResponse>> getAlertDetail(Long alertId, CurrentUser currentUser);

    @Operation(
            summary = "최근 일주일 알림 목록 조회",
            description = """
                    보호자 홈 화면의 알림 목록용. 보호자만 가능.

                    최근 7일 이내에 발생한 자기 피보호자의 알림을 **KST 날짜별로 묶어서** 반환한다.
                    `data`의 키가 날짜(`yyyy-MM-dd`)이고 값이 그 날의 알림 배열이다.

                    - 기준은 조회 시점으로부터 **정확히 168시간 전**이다. 날짜 경계가 아니므로
                      7일 전 같은 날짜라도 시각이 이르면 제외된다
                    - 날짜는 최신순, 각 날짜 안의 알림도 최신순으로 정렬된다
                    - `occurredAt`은 **KST**다. 그룹 키(날짜)와 같은 기준이다
                    - 해당 기간에 알림이 없으면 `data`는 빈 객체(`{}`)다
                    - `presignedUrl`은 조회 시점 발급이라 만료된다. 저장하지 말 것

                    **JSON 객체의 키 순서는 규격상 보장되지 않는다.** 화면에서 날짜 정렬이 필요하면
                    클라이언트에서 키를 정렬해 사용한다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "3"))
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
                                                    name = "알림이 있을 때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "ALERT_READ",
                                                                "message": "알림 내용이 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "2026-08-05": [
                                                                        {
                                                                            "type": "OBSTACLE",
                                                                            "occurredAt": "2026-08-05T18:55:00",
                                                                            "occurredPlace": "서울특별시 종로구 삼청로 37",
                                                                            "presignedUrl": "https://onvision-dev.s3.ap-northeast-2.amazonaws.com/alerts/...?X-Amz-Signature=...",
                                                                            "content": "보행로에 배달 오토바이가 정차해 있습니다",
                                                                            "action": "우회 안내"
                                                                        },
                                                                        {
                                                                            "type": "OBSTACLE",
                                                                            "occurredAt": "2026-08-05T07:05:00",
                                                                            "occurredPlace": "서울특별시 강남구 학동로 426",
                                                                            "presignedUrl": "https://onvision-dev.s3.ap-northeast-2.amazonaws.com/alerts/...?X-Amz-Signature=...",
                                                                            "content": "전방 2m에 자전거가 세워져 있습니다",
                                                                            "action": "위험 음성 재생"
                                                                        }
                                                                    ],
                                                                    "2026-08-03": [
                                                                        {
                                                                            "type": "OBSTACLE",
                                                                            "occurredAt": "2026-08-03T08:45:00",
                                                                            "occurredPlace": "서울특별시 서초구 강남대로 405-2",
                                                                            "presignedUrl": "https://onvision-dev.s3.ap-northeast-2.amazonaws.com/alerts/...?X-Amz-Signature=...",
                                                                            "content": "횡단보도 앞에 화분이 놓여 있습니다",
                                                                            "action": "위험 음성 재생"
                                                                        }
                                                                    ]
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "알림이 없을 때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "ALERT_READ",
                                                                "message": "알림 내용이 정상적으로 조회되었습니다.",
                                                                "data": {}
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "피보호자 권한으로 실행했을 때",
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
                    description = "이 보호자에게 연결된 피보호자가 없을 때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_RELATION",
                                                        "message": "보호자와 피보호자의 관계를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @ApiUnauthorized
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<Map<LocalDate, List<AlertResponse>>>> getAlertsInWeek(CurrentUser currentUser);

    /** Swagger UI가 멀티파트 두 파트를 각각 렌더링하도록 하기 위한 문서 전용 스키마 */
    @Schema(name = "ObstacleDetectMultipart")
    class ObstacleDetectMultipart {
        @Schema(description = "감지 정보 JSON", implementation = ObstacleRequest.class)
        public ObstacleRequest request;

        @Schema(description = "감지 시점 캡처 이미지", type = "string", format = "binary")
        public MultipartFile image;
    }
}
