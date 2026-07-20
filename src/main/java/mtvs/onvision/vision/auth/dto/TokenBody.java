package mtvs.onvision.vision.auth.dto;

import mtvs.onvision.vision.user.domain.UserRole;

public record TokenBody(
        Long userId,
        String email,
        UserRole role
) {
}
