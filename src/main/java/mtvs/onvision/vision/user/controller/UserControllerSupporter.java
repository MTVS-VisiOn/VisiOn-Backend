package mtvs.onvision.vision.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.user.dto.SignupRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name="User API", description = "회원가입 및 사용자 기능 API")
public interface UserControllerSupporter {

    @Operation(
            summary = "회원가입",
            description = "회원가입 API"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "회원가입 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "USER_CREATED",
                                                        "message": "계정이 정상적으로 생성되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식이 잘못되었을떄(이메일이 비었을때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "이메일은 필수입니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식이 잘못되었을떄(보호자인데 피보호자의 내용이 없을때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "INVALID_WARD",
                                                        "message": "보호자일 경우 피보호자는 필수입니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> signup(SignupRequest request);

}
