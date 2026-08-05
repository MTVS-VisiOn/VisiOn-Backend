package mtvs.onvision.vision.auth.controller;

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
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.auth.dto.LoginRequest;
import mtvs.onvision.vision.auth.dto.LogoutRequest;
import mtvs.onvision.vision.auth.dto.RefreshRequest;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name="Auth API", description = "로그인, 토큰 갱신, 로그아웃 API")
public interface AuthControllerSupporter {
    @Operation(
            summary = "로그인",
            description = "로그인 API",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = {
                            @ExampleObject(
                                    name = "피보호자(WARD) 로그인",
                                    value = """
                                        {
                                            "email": "test@naver.com",
                                            "password": "test1234"
                                        }
                                        """
                            ),
                            @ExampleObject(
                                    name = "보호자(GUARDIAN) 로그인",
                                    value = """
                                        {
                                            "email": "test1@naver.com",
                                            "password": "test1234"
                                        }
                                        """
                            )
                    }
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "LOGIN_SUCCESS",
                                                        "message": "로그인에 성공했습니다.",
                                                        "data": {
                                                            "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiZW1haWwiOiJ0ZXN0MUBuYXZlci5jb20iLCJyb2xlIjoiR1VBUkRJQU4iLCJpYXQiOjE3ODQxODQ4NDksImV4cCI6MTc4NDE4NTc0OX0.dASCQI99m1Y9DuENV32VcpuPYRllkA7cyAHdryQwXfGmjkEoqiyVSA9VSTq3ZSzkuMUNed5TsVFa79yJlGTMDg",
                                                            "refreshToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiZW1haWwiOiJ0ZXN0MUBuYXZlci5jb20iLCJyb2xlIjoiR1VBUkRJQU4iLCJpYXQiOjE3ODQxODQ4NDksImV4cCI6MTc4NDc4OTY0OX0.ukCrFMMkkYyXON2ieAqUe3njb-wjNpYn1SY-N2qaGKTHsPvYNYNEs6K0sejMatqFb37xKEc8-7STLoqV9DTT3w"
                                                        }
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
            )
    })
    ResponseEntity<ApiResult<KeyPair>> login(LoginRequest request);

    @Operation(
            summary = "토큰 갱신",
            description = "accessToken은 빨리 만료되므로 주기적으로 refreshToken으로 accessToken 갱신하는 API",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "2"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 갱신 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "REFRESH_SUCCESS",
                                                        "message": "토큰 갱신에 성공했습니다.",
                                                        "data": {
                                                            "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiZW1haWwiOiJ0ZXN0MUBuYXZlci5jb20iLCJyb2xlIjoiR1VBUkRJQU4iLCJpYXQiOjE3ODQxODU5NjksImV4cCI6MTc4NDE4Njg2OX0.lm-v2NW4SHyGFJcs9Br7Fd6okwMt7zhsLLmME8AvSm-tgeku1Vzi7kI_fuWOjkKgEwHbwGcU-HmJI1Zj3jwqmQ",
                                                            "refreshToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiZW1haWwiOiJ0ZXN0MUBuYXZlci5jb20iLCJyb2xlIjoiR1VBUkRJQU4iLCJpYXQiOjE3ODQxODU5NjksImV4cCI6MTc4NDc5MDc2OX0.IIV2yFM6YyUch129D6uvsBSL6rB6QDMFrpXNeiq_QDCwSzDolg3-RR1rel28qWVQ6pWB7-7fcb87H4afnDBqXw"
                                                        }
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "요청 형식이 잘못되었을떄(refreshToken이 만료되었을때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "INVALID_REFRESH_TOKEN",
                                                        "message": "refresh 토큰이 맞지 않습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<KeyPair>> refreshToken(RefreshRequest request);

    @Operation(
            summary = "로그아웃",
            description = """
                    로그아웃 API. refreshToken을 삭제한다.

                    body에 해당 기기의 fid를 함께 보내면 그 기기의 푸시 등록도 함께 해제한다.
                    fid는 선택값이며, 생략하거나 body 자체를 보내지 않아도 로그아웃은 정상 처리된다.
                    (다중 기기를 등록한 사용자의 다른 기기는 영향받지 않는다.)
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "3"))
    )
    @RequestBody(
            required = false,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(
                            value = """
                                    {
                                        "fid": "dEXAMPLEfid1234567890"
                                    }
                                    """
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 성공",
                    content = {
                            @Content(
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "LOGOUT_SUCCESS",
                                                        "message": "로그아웃에 성공했습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<SuccessCode>> logout(LogoutRequest request, CurrentUser currentUser);
}
