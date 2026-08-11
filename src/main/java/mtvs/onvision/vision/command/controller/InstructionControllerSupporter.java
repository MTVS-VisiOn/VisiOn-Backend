package mtvs.onvision.vision.command.controller;

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
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.dto.InstructionResponse;
import mtvs.onvision.vision.common.response.ApiResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "instruction API", description = "빠른 지시 문구 API")
public interface InstructionControllerSupporter {

    @Operation(
            summary = "빠른 지시 문구 등록",
            description = """
                    보호자만 가능. 자주 쓰는 지시 문구를 저장한다.

                    여기 저장하는 것은 **문구 프리셋일 뿐 지시를 보내는 것이 아니다.**
                    실제 전송은 `POST /api/commands/instruction`이다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
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
                                                        "code": "INSTRUCTION_CREATED",
                                                        "message": "빠른 지시어가 정상적으로 저장되었습니다.",
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
            )
    })
    ResponseEntity<ApiResult<Void>> saveInstruction(InstructionRequest request,
                                                    CurrentUser currentUser);

    @Operation(
            summary = "빠른 지시 문구 목록 조회",
            description = """
                    보호자만 가능. 본인이 등록한 문구만 나온다.

                    **정렬 순서는 보장되지 않는다.** 화면에서 순서가 중요하면 클라이언트가 `id` 기준으로 정렬한다.
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
                                                    name = "등록한 문구가 있을때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "INSTRUCTION_READ",
                                                                "message": "빠른 지시어가 정상적으로 조회되었습니다.",
                                                                "data": [
                                                                    { "id": 1, "content": "잠시 멈추세요." },
                                                                    { "id": 2, "content": "횡단보도 입니다." },
                                                                    { "id": 3, "content": "조심하세요." }
                                                                ]
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "등록한 문구가 없을때",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "INSTRUCTION_READ",
                                                                "message": "빠른 지시어가 정상적으로 조회되었습니다.",
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
            )
    })
    ResponseEntity<ApiResult<List<InstructionResponse>>> getInstructions(CurrentUser currentUser);

    @Operation(
            summary = "빠른 지시 문구 수정",
            description = """
                    보호자만 가능. 본인이 등록한 문구만 수정할 수 있다.

                    없는 id는 404, 남의 문구는 403이다. 즐겨찾기(`PATCH /api/favorites/{id}`)는
                    id 존재 여부를 감추려고 둘 다 404를 주지만 여기는 구분해서 준다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "3"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "INSTRUCTION_UPDATED",
                                                        "message": "빠른 지시어가 정상적으로 수정되었습니다.",
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
                    description = "다른 보호자가 등록한 문구일때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_OWNER",
                                                        "message": "주인이 아닙니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "없는 id일때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_INSTRUCTION",
                                                        "message": "빠른 지시어를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> updateInstruction(
            @Parameter(description = "수정할 문구 id", example = "10") Long instructionId,
            InstructionRequest request,
            CurrentUser currentUser);

    @Operation(
            summary = "빠른 지시 문구 삭제",
            description = """
                    보호자만 가능. 본인이 등록한 문구만 삭제할 수 있다.

                    **소프트 삭제가 아니라 행을 실제로 지운다.** 되돌릴 수 없다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "4"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "INSTRUCTION_DELETED",
                                                        "message": "빠른 지시어가 정상적으로 삭제되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "다른 보호자가 등록한 문구일때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_OWNER",
                                                        "message": "주인이 아닙니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "없는 id이거나 이미 삭제됐을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_INSTRUCTION",
                                                        "message": "빠른 지시어를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> deleteInstruction(
            @Parameter(description = "삭제할 문구 id", example = "10") Long instructionId,
            CurrentUser currentUser);
}
