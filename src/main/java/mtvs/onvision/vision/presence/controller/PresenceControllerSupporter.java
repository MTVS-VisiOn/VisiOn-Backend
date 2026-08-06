package mtvs.onvision.vision.presence.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.presence.dto.HeartbeatRequest;
import mtvs.onvision.vision.presence.dto.PresenceResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name="Presence API", description = "기기 연결상태 API")
public interface PresenceControllerSupporter {

    @Operation(
            summary = "피보호자 기기 연결 상태 전송",
            description = """
                    피보호자만 가능.

                    생존신호를 저장하는 것 외에 **배터리 부족 알림**이라는 부수효과가 있다.

                    ## 배터리 부족 알림
                    직전 heartbeat의 `battery`와 비교해 임계값(`20`, `10`, `5`)을 **새로 내려간 순간**에만
                    보호자에게 푸시가 나간다. 잔량이 임계값 아래에 있다는 것만으로는 발송하지 않는다.

                    | 이전 → 현재 | 발송 |
                    |---|---|
                    | 22 → 18 | O — 정확히 20을 밟지 않아도 지나갔으면 발송한다 |
                    | 20 → 20 | X — 머무르는 동안 heartbeat마다 반복 발송하지 않는다 |
                    | 18 → 17 | X — 임계값을 지나지 않았다 |
                    | 15 → 20 | X — 충전으로 올라간 것은 사건이 아니다 |
                    | 15 → 4 | O — 10과 5를 함께 지나도 **한 건만** 나가고 문구에는 현재 잔량(4%)이 들어간다 |

                    **직전 heartbeat가 없으면 판정하지 않는다.** 첫 신호이거나 신호가 180초(presence TTL)
                    넘게 끊겼다 돌아온 경우가 여기 해당한다. 비교 대상이 없으면 '내려갔다'를 알 수 없기 때문이며,
                    이 구간에 지나간 임계값은 건너뛴다.

                    **발송은 비동기다.** 201을 받았다고 알림이 갔다는 뜻이 아니고, 발송 실패도 이 응답에
                    반영되지 않는다.

                    알림 이력은 `GET /api/alerts/lastweek`과 `GET /api/alerts/{alertId}`에 `LOW_BATTERY`
                    타입으로 남는다. 이미지와 위치가 없는 알림이라 **`presignedUrl`과 `occurredPlace`가 null**이다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "전송 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "HEARTBEAT_CREATED",
                                                        "message": "생존신호가 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식이 잘못되었을떄(배터리값이 비었을때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "배터리 상태는 필수값입니다.",
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
    ResponseEntity<ApiResult<Void>> receiveHeartBeat(HeartbeatRequest request,
                                                     CurrentUser currentUser);


    @Operation(
            summary = "보호자 피보호자 기기 연결 상태 조회",
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
                                                    name = "정상",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "PRESENCE_READ",
                                                                "message": "기기 상태 확인이 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "battery": 77,
                                                                    "deviceConnected": true,
                                                                    "deviceNetwork": true,
                                                                    "status": "정상"
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "기기는 붙어 있고 인터넷만 끊겼을 때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "PRESENCE_READ",
                                                                "message": "기기 상태 확인이 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "battery": 77,
                                                                    "deviceConnected": true,
                                                                    "deviceNetwork": false,
                                                                    "status": "네트워크 중단"
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "생존신호가 없을 때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "PRESENCE_READ",
                                                                "message": "기기 상태 확인이 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "battery": null,
                                                                    "deviceConnected": false,
                                                                    "deviceNetwork": false,
                                                                    "status": "연결 없음"
                                                                }
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
    ResponseEntity<ApiResult<PresenceResponse>> getWardPresence(CurrentUser currentUser);

}
