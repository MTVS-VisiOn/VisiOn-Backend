package mtvs.onvision.vision.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.user.dto.ResisterGuardianResponse;
import mtvs.onvision.vision.user.dto.SettingRequest;
import mtvs.onvision.vision.user.dto.SignupRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name="User API", description = "회원가입 및 사용자 기능 API")
public interface UserControllerSupporter {

    @Operation(
            summary = "회원가입",
            description = "회원가입 API",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = {
                            @ExampleObject(
                                    name = "피보호자(WARD) 회원가입",
                                    value = """
                                        {
                                            "email":"test2@naver.com",
                                            "password":"test1234",
                                            "nickname":"test2",
                                            "phoneNumber":"010-0000-0002",
                                            "role":"WARD"
                                        }
                                        """
                            ),
                            @ExampleObject(
                                    name = "보호자(GUARDIAN) 회원가입",
                                    value = """
                                        {
                                            "email":"test3@naver.com",
                                            "password":"test1234",
                                            "nickname":"test3",
                                            "phoneNumber":"010-0000-0003",
                                            "role":"GUARDIAN",
                                            "registerToken": "피보호자의 등록 토큰값을 넣어주세요"
                                        }
                                        """
                            )
                    }
            )
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
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "등록 토큰이 이미 만료되었을떄",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "INVALID_REGISTER_TOKEN",
                                                        "message": "register 토큰이 맞지 않습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> signup(SignupRequest request);


    @Operation(
            summary = "피보호자 등록 토큰 생성",
            description = "피보호자 등록 토큰 생성 API",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "2"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "등록 토큰 생성 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "REGISTER_TOKEN_CREATED",
                                                        "message": "보호자 등록 토큰이 정상적으로 생성되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<ResisterGuardianResponse>> getGuardianRegisterToken(CurrentUser currentUser);

    @Operation(
            summary = "보호자 설정 세팅",
            description = "보호자의 앱의 경우 알림 설정이 가능",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "3"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "세팅 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "SETTING_SUCCESS",
                                                        "message": "설정이 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식이 잘못되었을떄(필수 세팅값이 null일때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "필수 설정입니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<Void>> updateGuardianSettings(SettingRequest request,
                                                           CurrentUser currentUser);

}
