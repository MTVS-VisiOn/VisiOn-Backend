package mtvs.onvision.vision.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "refreshToken은 필수값입니다.")
        @Schema(
                examples = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiZW1haWwiOiJ0ZXN0MUBuYXZlci5jb20iLCJyb2xlIjoiR1VBUkRJQU4iLCJpYXQiOjE3ODQxODU5NTMsImV4cCI6MTc4NDc5MDc1M30.ljmI1Bl1DuJTFIZua7jGcH50q_M5wbELbOZhgi2HCIQrMKxTEQuOJgr7CptF2qDRYQccQ2-gxZtIjTajJUg1lw",
                description = "이전 로그인때 받은 refreshToken값",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String refreshToken
) {
}
