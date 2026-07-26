package mtvs.onvision.vision.location.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.location.dto.LastLocationResponse;
import mtvs.onvision.vision.location.dto.LocationRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name="location API", description = "기기 연결상태 API")
public interface LocationControllerSupporter {

    @Operation(
            summary = "피보호자 실시간 위치 전송",
            description = "피보호자만 가능",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "전송 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "LOCATION_CREATED",
                                                        "message": "실시간 위치가 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식이 잘못되었을떄(경도값이 비었을때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "경도는 필수값입니다.",
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
    ResponseEntity<ApiResult<Void>> receiveLocation(@RequestBody @Valid LocationRequest request,
                                                    @AuthenticationPrincipal CurrentUser currentUser);

    @Operation(
            summary = "보호자 피보호자 실시간 위치 조회",
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
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "LOCATION_READ",
                                                        "message": "실시간 위치가 정상적으로 조회되었습니다.",
                                                        "data": {
                                                            "isCponnected": true,
                                                            "lastAddress": "서울특별시 강남구 테헤란로 212 멀티캠퍼스",
                                                            "status": "도보로 이동중"
                                                        }
                                                    }
                                                    """
                                    )
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
    ResponseEntity<ApiResult<LastLocationResponse>> getLastLocation(@AuthenticationPrincipal CurrentUser currentUser);
}
