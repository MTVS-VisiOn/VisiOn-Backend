package mtvs.onvision.vision.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
    /*User*/
    USER_CREATED("계정이 정상적으로 생성되었습니다."),
    REGISTER_TOKEN_CREATED("보호자 등록 토큰이 정상적으로 생성되었습니다."),
    SETTING_SUCCESS("설정이 정상적으로 저장되었습니다."),


    /*Auth*/
    LOGIN_SUCCESS("로그인에 성공했습니다."),
    REFRESH_SUCCESS("토큰 갱신에 성공했습니다."),
    LOGOUT_SUCCESS("로그아웃에 성공했습니다."),

    /*Presence*/
    HEARTBEAT_CREATED("생존신호가 정상적으로 저장되었습니다."),
    PRESENCE_READ("기기 상태 확인이 정상적으로 조회되었습니다."),

    /*Location*/
    LOCATION_CREATED("실시간 위치가 정상적으로 저장되었습니다."),
    LOCATION_READ("실시간 위치가 정상적으로 조회되었습니다."),
    LOCATION_SEARCH_READ("장소 검색이 정상적으로 조회되었습니다."),

    /*Favorite*/
    FAVORITE_CREATED("즐겨찾기가 정상적으로 저장되었습니다."),
    FAVORITE_READ("즐겨찾기가 정상적으로 조회되었습니다."),
    FAVORITE_UPDATED("즐겨찾기가 정상적으로 수정되었습니다."),
    FAVORITE_DELETED("즐겨찾기가 정상적으로 삭제되었습니다."),

    /*Navigation*/
    NAVIGATION_SEARCH("경로가 성공적으로 검색되었습니다."),


    /*common*/
    BUSINESS_SUCCESS("정상적으로 작성되었습니다.");

    private final String successMessage;


}
