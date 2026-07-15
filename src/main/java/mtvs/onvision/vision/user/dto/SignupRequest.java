package mtvs.onvision.vision.user.dto;

import jakarta.validation.constraints.*;
import mtvs.onvision.vision.user.domain.UserRole;

public record SignupRequest(

        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 최대 50자까지 가능합니다.")
        String nickname,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$",
                message = "전화번호 형식은 010-0000-0000로 작성해주세요."
        )
        String phoneNumber,
        @NotNull(message = "유저의 역할은 필수입니다.")
        UserRole role,
        Long wardId

        ) {
}
