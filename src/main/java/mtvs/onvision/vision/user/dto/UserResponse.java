package mtvs.onvision.vision.user.dto;

import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;

public record UserResponse(
        Long id,
        String email,
        UserRole role,
        String nickname,
        WardInfo ward
) {
    public static UserResponse from(User guardian, User ward) {
        return new UserResponse(guardian.getId(), guardian.getEmail(), guardian.getRole(), guardian.getNickname(),
                new WardInfo(ward.getId(), ward.getNickname(), ward.getPhoneNumber()));
    }

    public static UserResponse from(User ward) {
        return new UserResponse(ward.getId(), ward.getEmail(), ward.getRole(), ward.getNickname(), null);
    }

    public record WardInfo(
            Long id,
            String nickname,
            String phoneNumber
    ){}
}
