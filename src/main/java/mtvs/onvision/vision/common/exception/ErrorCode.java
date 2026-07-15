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
    NOT_FOUND_WARD(HttpStatus.NOT_FOUND, "해당하는 아이디의 피보호자를 찾을 수 없습니다."),
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, "해당하는 아이디의 유저를 찾을 수 없습니다."),
    NOT_FOUND_REFRESH(HttpStatus.NOT_FOUND, "해당하는 refresh 토큰을 찾을 수 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "refresh 토큰이 맞지 않습니다."),

    /*common*/
    REQUESTPARAM_REQUIRED(HttpStatus.BAD_REQUEST, "해당 파라미터는 필수값입니다. :::"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "검증에 실패했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),
    BUSINESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 오류입니다.");

    private final HttpStatus status;
    private final String message;
}
