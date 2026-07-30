package mtvs.onvision.vision.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /*User*/
    INVALID_WARD(HttpStatus.BAD_REQUEST, "보호자일 경우 피보호자는 필수입니다."),
    EXIST_EMAIL(HttpStatus.CONFLICT, "중복된 이메일입니다."),
    EXIST_PHONENUMBER(HttpStatus.CONFLICT, "중복된 전화번호입니다."),
    EXIST_GUARDIAN(HttpStatus.CONFLICT, "해당 피보호자에게는 이미 등록된 보호자가 있습니다."),
    NOT_FOUND_WARD(HttpStatus.NOT_FOUND, "해당하는 아이디의 피보호자를 찾을 수 없습니다."),
    NOT_FOUND_GUARDIAN(HttpStatus.NOT_FOUND, "해당하는 아이디의 보호자를 찾을 수 없습니다."),
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "해당하는 아이디의 유저를 찾을 수 없습니다."),
    NOT_FOUND_REFRESH(HttpStatus.NOT_FOUND, "해당하는 refresh 토큰을 찾을 수 없습니다."),
    NOT_FOUND_REGISTER(HttpStatus.NOT_FOUND, "해당하는 register 토큰을 찾을 수 없습니다."),
    NOT_FOUND_RELATION(HttpStatus.NOT_FOUND, "해당하는 relation 을 찾을 수 없습니다."),
    NOT_MATCH_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "refresh 토큰이 맞지 않습니다."),
    INVALID_REGISTER_TOKEN(HttpStatus.BAD_REQUEST, "register 토큰이 맞지 않습니다."),

    /*signalling*/
    MAX_CONNECT(HttpStatus.CONFLICT, "이미 연결 중인 세션이 있어 참여할 수 없습니다."),
    EXIST_CONNECTION(HttpStatus.CONFLICT, "이미 연결된 상태입니다."),
    NOT_FOUND_ROOM(HttpStatus.CONFLICT, "연결된 세션 방이 없습니다."),
    FORBIDDEN_WARD(HttpStatus.FORBIDDEN, "피보호자는 권한이 없는 기능입니다."),
    NOT_DELETE_ROOM(HttpStatus.INTERNAL_SERVER_ERROR, "방이 지워지지 않았습니다."),
    INVALID_MESSAGE_TYPE(HttpStatus.BAD_REQUEST, "메세지 타입을 인식할 수 없습니다."),

    /*Presence*/
    NOT_FOUND_CONNECT(HttpStatus.NOT_FOUND, "기기의 연결상태를 찾을 수 없습니다."),
    /*Location*/
    NOT_FOUND_LAST_LOCATION(HttpStatus.NOT_FOUND, "마지막 위치를 찾을 수 없습니다."),
    TMAP_API_ERROR(HttpStatus.BAD_GATEWAY, "지도 서비스 호출에 실패했습니다."),

    /*Favorite*/
    EXIST_FAVORITE(HttpStatus.CONFLICT, "이미 저장된 장소입니다."),
    NOT_FOUND_FAVORITE(HttpStatus.NOT_FOUND, "해당 아이디의 즐겨찾기를 찾을 수 없습니다."),

    /*Navigation*/
    NOT_FOUND_TMAP_ROUTE(HttpStatus.NOT_FOUND, "티맵에서 경로를 찾을 수 없습니다."),
    INVALID_LOCATION(HttpStatus.BAD_REQUEST, "잘못된 장소가 입력되었습니다."),
    INVALID_TRANSFER(HttpStatus.BAD_REQUEST, "잘못된 이동수단이 입력되었습니다."),
    NOT_IN_SERVICE(HttpStatus.NOT_FOUND, "지금은 운행 중인 대중교통이 없습니다."),
    INVALID_TRANSIT_INDEX(HttpStatus.BAD_REQUEST, "대중교통 경로의 인덱스가 없습니다."),
    NOT_FOUND_ROUTE(HttpStatus.NOT_FOUND, "경로를 찾을 수 없습니다."),


    /*common*/
    REQUESTPARAM_REQUIRED(HttpStatus.BAD_REQUEST, "해당 파라미터는 필수값입니다. :::"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "검증에 실패했습니다."),
    ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 상태입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),
    BUSINESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 오류입니다. ::: ");

    private final HttpStatus status;
    private final String message;
}
