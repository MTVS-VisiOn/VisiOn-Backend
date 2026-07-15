package mtvs.onvision.vision.user.domain;

public enum UserRole {
    WARD, GUARDIAN;

    public static UserRole of(String role) {
        for (UserRole v : values()) {
            if (v.name().equalsIgnoreCase(role)) {
                return v;
            }
        }
        return null;
    }
}
