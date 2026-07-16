package mtvs.onvision.vision.common.swagger;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "401",
        description="토큰이 없거나 유효하지 않음",
        content = {
                @Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(
                                value = """
                                        {
                                            "success": false,
                                            "code": "UNAUTHORIZED",
                                            "message": "계정 인증이 필요합니다.",
                                            "data": null
                                        }
                                        """
                        )
                )
        }
)
public @interface ApiUnauthorized {
}
