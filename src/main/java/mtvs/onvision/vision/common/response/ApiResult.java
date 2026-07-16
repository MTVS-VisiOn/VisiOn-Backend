package mtvs.onvision.vision.common.response;

import mtvs.onvision.vision.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public record ApiResult<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    private static <T> ApiResult<T> success(SuccessCode code, T data) {
        return new ApiResult<>(
                true,
                code.name(),
                code.getSuccessMessage(),
                data
        );
    }

    private static <T> ApiResult<T> fail(ErrorCode code) {
        return new ApiResult<>(
                false,
                code.name(),
                code.getMessage(),
                null
        );
    }
    private static <T> ApiResult<T> fail(ErrorCode code, String message) {
        return new ApiResult<>(
                false,
                code.name(),
                message,
                null
        );
    }

    public static <T> ResponseEntity<ApiResult<T>> ok(SuccessCode code, T data) {
        return ResponseEntity.ok(success(code, data));
    }
    public static <T> ResponseEntity<ApiResult<T>> ok(SuccessCode code) {
        return ResponseEntity.ok(success(code, null));
    }

    public static <T> ResponseEntity<ApiResult<T>> created(SuccessCode code, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(code, data));
    }
    public static <T> ResponseEntity<ApiResult<T>> created(SuccessCode code) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(code, null));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(fail(code));
    }
    public static <T> ResponseEntity<ApiResult<T>> error(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(fail(code, message));
    }
}
