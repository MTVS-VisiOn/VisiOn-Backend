package mtvs.onvision.vision.auth.domain;

public enum TokenType {
    ACCOUNT, DEVICE;

    public static TokenType of(String type) {
        for (TokenType v : values()) {
            if (v.name().equalsIgnoreCase(type)) {
                return v;
            }
        }
        return ACCOUNT;
    }
}
