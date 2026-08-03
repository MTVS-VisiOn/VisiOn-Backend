package mtvs.onvision.vision.user.dto;

import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;

public record GuardianResponse(
        Long id,
        String email,
        UserRole role,
        String nickname,
        WardInfo ward
) {
    public static GuardianResponse from(User guardian, User ward) {
        return new GuardianResponse(guardian.getId(), guardian.getEmail(), guardian.getRole(), guardian.getNickname(),
                new WardInfo(ward.getId(), ward.getNickname(), ward.getPhoneNumber()));
    }

    public record WardInfo(
            Long id,
            String nickname,
            String phoneNumber
    ){}
}
