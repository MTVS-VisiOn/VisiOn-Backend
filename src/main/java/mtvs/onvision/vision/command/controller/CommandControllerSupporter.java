package mtvs.onvision.vision.command.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.dto.CommandResponse;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.common.response.ApiResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "command API", description = "보호자 지시 API")
public interface CommandControllerSupporter {

    @Operation(
            summary = "피보호자에게 지시 전송",
            description = """
                    보호자만 가능. 지시를 저장한 뒤 피보호자 기기로 FCM data 메시지를 보낸다.

                    **응답은 저장 성공까지만 보장한다.** 서버는 기기 도달 여부를 알 수 없고 확인도 받지 않는다.
                    피보호자 앱이 꺼져 있거나 등록된 기기가 없으면 지시는 조용히 폐기된다 — 이 경우에도 201이다.
                    나중에 배달되면 위험한 데이터라 재전송하지 않는다.

                    앱은 수신 즉시 음성으로 읽는다. `content`는 그대로 발화되므로 빈 값을 보낼 수 없다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "저장 성공 (기기 도달을 뜻하지 않는다)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "COMMAND_CREATED",
                                                        "message": "지시어가 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "content가 비었거나 공백뿐일때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "공백일 수 없습니다",
                                                        "data": null
                                                    }
                                                    """
                                    )
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
                    description = "보호자에게 연결된 피보호자가 없을때",
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
    ResponseEntity<ApiResult<Void>> guardianInstruct(InstructionRequest request,
                                                     CurrentUser currentUser);

    @Operation(
            summary = "최근 지시 조회",
            description = """
                    보호자만 가능. 자신의 피보호자가 **오늘 받은 지시 전부**를 최신순으로 준다.

                    기준은 **오늘 00:00(KST) 이후**다. 건수 상한과 페이지 파라미터는 없다.
                    경계는 서버 시간대가 아니라 KST로 계산하므로 배포 환경과 무관하게 같다.
                    **자정이 지나면 목록이 비워진다** — 어제 보낸 지시는 조회되지 않는다.

                    `occurredAt`은 지시가 만들어진 시각(UTC)이며 FCM으로 앱에도 같은 값이 전달된다.
                    전달 성공 여부는 담기지 않는다 — 목록에 있다는 것은 보냈다는 뜻이지 읽혔다는 뜻이 아니다.
                    """,
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
                                                    name = "지시가 있을때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "COMMAND_READ",
                                                                "message": "지시어가 정상적으로 조회되었습니다.",
                                                                "data": [
                                                                    {
                                                                        "id": 3,
                                                                        "content": "잠시 멈추세요.",
                                                                        "occurredAt": "2026-08-10T05:31:00Z"
                                                                    },
                                                                    {
                                                                        "id": 2,
                                                                        "content": "횡단보도 입니다.",
                                                                        "occurredAt": "2026-08-10T05:29:12Z"
                                                                    }
                                                                ]
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "보낸 지시가 없을때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "COMMAND_READ",
                                                                "message": "지시어가 정상적으로 조회되었습니다.",
                                                                "data": []
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
                    description = "보호자에게 연결된 피보호자가 없을때",
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
    ResponseEntity<ApiResult<List<CommandResponse>>> getInstructs(CurrentUser currentUser);
}
