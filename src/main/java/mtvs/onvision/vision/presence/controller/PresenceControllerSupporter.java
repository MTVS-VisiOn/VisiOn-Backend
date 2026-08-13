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

                    생존신호를 저장하는 것 외에 **배터리 부족 알림**과 **연결 끊김 알림**이라는 부수효과가 있다.
                    둘 다 보호자에게 푸시로 나가고, 이 API의 응답에는 반영되지 않는다.

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

                    ## 연결 끊김 알림
                    **`network.connected`가 `true`인 heartbeat가 도착할 때만** 감시 목록의 시각이
                    `lastSync`로 갱신된다. 신호는 왔지만 네트워크가 끊겼다고 보고한 heartbeat는 갱신하지 않는다.
                    조회 API(`GET /api/presence`)의 연결 판정과 같은 기준을 쓰기 위해서다.

                    서버가 주기적으로 감시 목록을 훑어, 마지막 갱신이 **120초**를 넘긴 피보호자를 찾으면
                    보호자에게 푸시를 보내고 그 피보호자를 목록에서 지운다. 그래서 **끊겨 있는 동안 반복 발송되지 않고,
                    재연결해서 heartbeat가 다시 오면 그때부터 다시 감시 대상이 된다.**

                    감지는 주기적으로 돌기 때문에 **끊긴 시각과 푸시가 나가는 시각 사이에 최대 한 주기만큼 지연**이 있다.
                    푸시 제목의 시각은 감지한 시각이 아니라 **마지막으로 정상 연결이었던 시각**이다.

                    피보호자가 로그아웃하거나 앱을 종료해도 heartbeat가 끊기므로 같은 알림이 나간다.
                    고장과 구분되지 않는다.

                    ## 보호자 영상 상태
                    `guardianStreamStatus`는 Quest가 보고하는 **보호자 실시간 영상 연결 단계**다. 필수값이며,
                    조회 API(`GET /api/presence`)의 응답에 그대로 실린다.

                    | 값 | 뜻 |
                    |---|---|
                    | `idle` | 영상이 꺼져 있다. 초기 상태이자 종료 후 상태다 |
                    | `loading_ice_servers` | `GET /api/ice-servers` 호출 중 |
                    | `connecting_signaling` | `/signal-raw` 웹소켓 연결 중 |
                    | `waiting_for_guardian` | 방에 들어갔고 보호자를 기다리는 중 |
                    | `negotiating` | offer/answer/candidate 교환 중 |
                    | `streaming` | 영상이 흐르는 중 |
                    | `failed` | 실패 |
                    | `unknown` | **서버가 모르는 문자열을 받았을 때의 대체값.** 기기가 직접 보내는 값은 아니다 |

                    목록에 없는 문자열이 와도 400이 아니라 `unknown`으로 저장된다. 필드 자체가 없으면 400이다.

                    **값은 heartbeat 주기(30초)마다만 갱신된다.** 연결 단계는 초 단위로 지나가므로
                    조회 시점의 값이 최대 한 주기만큼 뒤처질 수 있다.

                    ## 공통
                    **발송은 비동기다.** 201을 받았다고 알림이 갔다는 뜻이 아니고, 발송 실패도 이 응답에
                    반영되지 않는다.

                    알림 이력은 `GET /api/alerts/lastweek`과 `GET /api/alerts/{alertId}`에 각각
                    `LOW_BATTERY`·`DISCONNECTED` 타입으로 남는다. 둘 다 이미지와 위치가 없는 알림이라
                    **`presignedUrl`과 `occurredPlace`가 null**이다.
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
                                                                    "status": "정상",
                                                                    "guardianStreamStatus": "streaming"
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
                                                                    "status": "네트워크 중단",
                                                                    "guardianStreamStatus": "idle"
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
                                                                    "status": "연결 없음",
                                                                    "guardianStreamStatus": null
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
