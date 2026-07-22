package mtvs.onvision.vision.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
    /*User*/
    USER_CREATED("계정이 정상적으로 생성되었습니다."),
    REGISTER_TOKEN_CREATED("보호자 등록 토큰이 정상적으로 생성되었습니다."),


    /*Auth*/
    LOGIN_SUCCESS("로그인에 성공했습니다."),
    REFRESH_SUCCESS("토큰 갱신에 성공했습니다."),
    LOGOUT_SUCCESS("로그아웃에 성공했습니다."),
    BUSINESS_SUCCESS("정상적으로 작성되었습니다.");

    private final String successMessage;


}
