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
import mtvs.onvision.vision.user.dto.UserResponse;
import mtvs.onvision.vision.user.dto.RegisterGuardianResponse;
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
                                                        "data": {
                                                            "registerToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiZW1haWwiOiJ0ZXN0MUBuYXZlci5jb20iLCJyb2xlIjoiR1VBUkRJQU4iLCJpYXQiOjE3ODQ2OTc2OTgsImV4cCI6MTc4NDY5ODU5OH0.JdRlH8l-sMTe9Z7QQQmxtLbgT9qNWWkuabcFkw8cpEWVgPGihH8u1HqLofCr80ejBYGA5hIfY6Buzu9-r5IyQA"
                                                        }
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<RegisterGuardianResponse>> getGuardianRegisterToken(CurrentUser currentUser);

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

    @Operation(
            summary = "계정 정보 조회",
            description = """
                    로그인한 본인의 계정 정보를 조회한다. **역할과 무관하게 같은 경로를 쓴다.**

                    `GUARDIAN`이면 연결된 피보호자 정보가 `ward`에 함께 담기고,
                    `WARD`이면 `ward`는 **`null`**이다""",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "4"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "계정 정보 조회 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "보호자(GUARDIAN)",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "USER_READ",
                                                                "message": "계정정보가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "id": 1,
                                                                    "role": "GUARDIAN",
                                                                    "nickname": "test1",
                                                                    "ward": {
                                                                        "id": 2,
                                                                        "nickname": "test2",
                                                                        "phoneNumber": "010-0000-0002"
                                                                    }
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "피보호자(WARD)",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "USER_READ",
                                                                "message": "계정정보가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "id": 2,
                                                                    "role": "WARD",
                                                                    "nickname": "test2",
                                                                    "ward": null
                                                                }
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "계정 또는 보호자-피보호자 관계를 찾을 수 없을 때",
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
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<UserResponse>> getUserInfo(CurrentUser currentUser);

}
