package mtvs.onvision.vision.user.dto;

import jakarta.validation.constraints.NotBlank;

public record FidRequest(
        @NotBlank
        String fid
) {
}
