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
import mtvs.onvision.vision.user.dto.DeviceRegisterRequest;
import mtvs.onvision.vision.user.dto.PairingDeviceResponse;
import mtvs.onvision.vision.user.dto.RegisterResponse;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.dto.UserResponse;
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
                                            "registerCode": "TV8HYB"
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
                    description = "등록 코드 형식이 올바르지 않을때(6자리가 아니거나 허용되지 않은 문자가 섞였을때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "INVALID_REGISTER_CODE",
                                                        "message": "등록 코드 형식이 올바르지 않습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "등록 코드가 만료되었거나 존재하지 않을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_REGISTER",
                                                        "message": "해당하는 register 코드를 찾을 수 없습니다.",
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
            summary = "피보호자 등록 코드 생성",
            description = """
                    피보호자 등록 코드 생성 API.

                    응답 필드는 `registerCode` 하나이며, 회원가입 시 같은 이름의 필드에 그대로 실어 보낸다.
                    코드는 6자리이고 `ABCDEFGHJKLMNPRSTUVWXY23456789`만 쓴다(I·O·Q·Z·0·1 제외).
                    유효 시간은 15분이며, 만료되면 재발급받아야 한다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "2"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "등록 코드 생성 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "REGISTER_CODE_CREATED",
                                                        "message": "등록 코드가 정상적으로 생성되었습니다.",
                                                        "data": {
                                                            "registerCode": "TV8HYB"
                                                        }
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "코드 생성이 재시도 상한까지 모두 충돌했을때(사실상 발생하지 않는다)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "FAILED_ISSUE_REGISTER_CODE",
                                                        "message": "등록 코드를 생성하는 것에 실패했습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<RegisterResponse>> getGuardianRegisterCode(CurrentUser currentUser);

    @Operation(
            summary = "기기 등록 코드 생성",
            description = """
                    Quest 기기 페어링용 일회용 코드 생성 API. 피보호자 본인만 발급할 수 있다.

                    응답 필드는 `registerCode` 하나이며, 모바일이 이 값을 QR과 6자리 코드로 표시한다.
                    코드는 6자리이고 `ABCDEFGHJKLMNPRSTUVWXY23456789`만 쓴다(I·O·Q·Z·0·1 제외).
                    **유효 시간은 3분**이며, 만료되면 재발급받아야 한다.

                    보호자 등록 코드와 문자 규격·응답 형태·성공 코드가 모두 같지만
                    **저장소가 분리돼 있어 서로 통용되지 않는다.**
                    회원가입(`registerCode`)에 이 코드를 넣으면 `NOT_FOUND_REGISTER`가 난다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "3"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "기기 등록 코드 생성 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "REGISTER_CODE_CREATED",
                                                        "message": "등록 코드가 정상적으로 생성되었습니다.",
                                                        "data": {
                                                            "registerCode": "TV8HYB"
                                                        }
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "코드 생성이 재시도 상한까지 모두 충돌했을때(사실상 발생하지 않는다)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "FAILED_ISSUE_REGISTER_CODE",
                                                        "message": "등록 코드를 생성하는 것에 실패했습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<RegisterResponse>> getDeviceRegisterCode(CurrentUser currentUser);

    @Operation(
            summary = "Quest 기기 페어링 (코드 → 토큰 교환)",
            description = """
                    Quest가 모바일 화면의 QR에서 읽은 일회용 코드를 기기용 access 토큰으로 교환한다.

                    **인증 없이 호출된다.** 이 시점에 Quest는 아무 토큰도 갖고 있지 않다.
                    코드의 TTL 3분과 6자리 규격이 유일한 방어선이다.

                    발급되는 토큰은 **피보호자(WARD) 권한의 access 토큰**이며 refresh 토큰은 주지 않는다.
                    만료되면 모바일이 재발급받아 BLE로 기기에 전달한다.

                    `deviceName`·`deviceSerialTail`은 저장하지 않고 로그로만 남긴다.
                    `deviceSerialTail`은 실제 하드웨어 시리얼이 아닐 수 있어 **인증 수단으로 쓰지 않는다.**
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "5"))
    )
    @RequestBody(
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(
                            value = """
                                {
                                    "code": "TV8HYB",
                                    "deviceName": "Meta Quest 3",
                                    "deviceSerialTail": "7F2C"
                                }
                                """
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "페어링 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "PAIRING_SUCCESS",
                                                        "message": "페어링 넘버가 확인되었습니다.",
                                                        "data": {
                                                            "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
                                                        }
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 필드가 비었을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "must not be blank",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "코드가 만료됐거나 존재하지 않을때 / 코드가 가리키는 피보호자가 없을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_REGISTER",
                                                        "message": "해당하는 register 코드를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<PairingDeviceResponse>> pairingDevice(DeviceRegisterRequest request);

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
