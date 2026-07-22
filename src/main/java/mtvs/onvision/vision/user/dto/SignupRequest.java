package mtvs.onvision.vision.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.GroupSequence;
import jakarta.validation.constraints.*;
import mtvs.onvision.vision.user.domain.UserRole;

@GroupSequence({SignupRequest.class, SignupRequest.FormatCheck.class})
public record SignupRequest(

        @Email(message = "올바른 이메일 형식이 아닙니다.", groups = FormatCheck.class)
        @NotBlank(message = "이메일은 필수입니다.")
        @Schema(
                examples = "test3@naver.com",
                description = "이메일 (이메일 형식 필수, 중복 불가)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.", groups = FormatCheck.class)
        @Schema(
                examples = "test1234",
                description = "비밀번호(최소 8자 이상)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 최대 50자까지 가능합니다.", groups = FormatCheck.class)
        @Schema(
                examples = "test3",
                description = "닉네임 (최대 50자)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String nickname,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$",
                message = "전화번호 형식은 010-0000-0000로 작성해주세요.",
                groups = FormatCheck.class
        )
        @Schema(
                examples = "010-0000-0003",
                description = "전화번호(010-0000-0000형식, 중복 불가)",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String phoneNumber,

        @NotNull(message = "유저의 역할은 필수입니다.")
        @Schema(
                examples = "GUARDIAN",
                description = "유저 역할, WARD 또는 GUARDIAN",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UserRole role,
        @Schema(
                examples = "3",
                description = "피보호자의 보호자 등록 토큰, 역할이 보호자일 경우 필수값"
        )
        String registerToken

        ) {
        public interface FormatCheck {}
}
