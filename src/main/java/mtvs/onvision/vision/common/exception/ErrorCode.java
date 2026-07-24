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

    /*Presence*/
    NOT_FOUND_CONNECT(HttpStatus.NOT_FOUND, "기기의 연결상태를 찾을 수 없습니다."),
    /*Location*/
    NOT_FOUND_LAST_LOCATION(HttpStatus.NOT_FOUND, "마지막 위치를 찾을 수 없습니다."),


    /*common*/
    REQUESTPARAM_REQUIRED(HttpStatus.BAD_REQUEST, "해당 파라미터는 필수값입니다. :::"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "검증에 실패했습니다."),
    ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 상태입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),
    BUSINESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 오류입니다.");

    private final HttpStatus status;
    private final String message;
}
