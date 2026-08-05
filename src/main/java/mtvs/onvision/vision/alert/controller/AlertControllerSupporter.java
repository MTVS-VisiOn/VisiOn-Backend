package mtvs.onvision.vision.alert.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.alert.dto.ObstacleRequest;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.swagger.ApiUnauthorized;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Alert API", description = "위험 알림 API")
public interface AlertControllerSupporter {

    @Operation(
            summary = "장애물 감지 전송",
            description = """
                    피보호자 기기(Quest)가 장애물을 감지했을 때 호출한다. 피보호자만 가능.

                    `multipart/form-data`로 두 파트를 보낸다.
                    - `request` : 감지 정보 JSON. **Content-Type을 `application/json`으로 지정해야 한다.** 지정하지 않으면 415가 난다
                    - `image` : 감지 시점 캡처 이미지

                    저장이 끝나면 보호자에게 푸시 알림이 비동기로 발송된다.
                    알림 발송 실패는 이 API의 응답에 영향을 주지 않는다(저장은 성공으로 응답한다).

                    `occurredAt`은 ISO-8601 UTC(`Z`)로 보낸다. 예) `2026-08-05T09:12:33.512Z`
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = ObstacleDetectMultipart.class),
                    encoding = {
                            @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE),
                            @Encoding(name = "image", contentType = "image/jpeg")
                    }
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "감지 정보 저장 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "DETECT_OBSTACLE_CREATED",
                                                        "message": "장애물 감지가 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식이 잘못되었을 때(좌표나 감지 시각이 비었을 때)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "위도는 필수값입니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을 때",
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
                    description = "피보호자에게 연결된 보호자가 없을 때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_RELATION",
                                                        "message": "보호자와 피보호자의 관계를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    @ApiUnauthorized
    @SecurityRequirement(name = "Bearer Authentication")
    ResponseEntity<ApiResult<Void>> detectObstacle(ObstacleRequest request,
                                                   MultipartFile image,
                                                   CurrentUser currentUser);

    /** Swagger UI가 멀티파트 두 파트를 각각 렌더링하도록 하기 위한 문서 전용 스키마 */
    @Schema(name = "ObstacleDetectMultipart")
    class ObstacleDetectMultipart {
        @Schema(description = "감지 정보 JSON", implementation = ObstacleRequest.class)
        public ObstacleRequest request;

        @Schema(description = "감지 시점 캡처 이미지", type = "string", format = "binary")
        public MultipartFile image;
    }
}
