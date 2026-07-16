package mtvs.onvision.vision.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        @Schema(
                examples = "test2@naver.com",
                description = "이메일 (이메일 형식 필수, 중복 불가)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
        @Schema(
                examples = "test1234",
                description = "비밀번호(최소 8자 이상)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String password
) {
}
