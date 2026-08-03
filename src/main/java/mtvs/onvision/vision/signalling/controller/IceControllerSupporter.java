package mtvs.onvision.vision.signalling.controller;

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
import mtvs.onvision.vision.signalling.dto.IceServersResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Signalling API", description = "영상통화 시그널링 API")
public interface IceControllerSupporter {

    @Operation(
            summary = "ICE 서버 목록 조회",
            description = "WebRTC 연결 직전에 호출한다. TURN credential 은 ttl(기본 1시간) 후 만료된다.",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
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
                                                        "code": "ICE_SERVERS_READ",
                                                        "message": "ICE 서버 정보가 정상적으로 조회되었습니다.",
                                                        "data": {
                                                            "iceServers": [
                                                                {
                                                                    "urls": ["stun:stun.l.google.com:19302"]
                                                                },
                                                                {
                                                                    "urls": [
                                                                        "turn:turn.example.com:3478?transport=udp",
                                                                        "turn:turn.example.com:3478?transport=tcp"
                                                                    ],
                                                                    "username": "1754200000:1",
                                                                    "credential": "8f2a...=="
                                                                }
                                                            ]
                                                        }
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "토큰이 없거나 만료됐을 때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "UNAUTHORIZED",
                                                        "message": "인증이 필요합니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<IceServersResponse>> getIceServers(CurrentUser currentUser);
}
