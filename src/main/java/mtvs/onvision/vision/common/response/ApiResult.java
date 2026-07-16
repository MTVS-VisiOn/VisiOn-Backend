package mtvs.onvision.vision.common.response;

import mtvs.onvision.vision.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ApiResult {
    private static <T> ApiResponses<T> success(SuccessCode code, T data) {
        return new ApiResponses<>(
                true,
                code.name(),
                code.getSuccessMessage(),
                data
        );
    }

    private static <T> ApiResponses<T> fail(ErrorCode code) {
        return new ApiResponses<>(
                false,
                code.name(),
                code.getMessage(),
                null
        );
    }
    private static <T> ApiResponses<T> fail(ErrorCode code, String message) {
        return new ApiResponses<>(
                false,
                code.name(),
                message,
                null
        );
    }

    public static <T> ResponseEntity<ApiResponses<T>> ok(SuccessCode code, T data) {
        return ResponseEntity.ok(success(code, data));
    }
    public static <T> ResponseEntity<ApiResponses<T>> ok(SuccessCode code) {
        return ResponseEntity.ok(success(code, null));
    }

    public static <T> ResponseEntity<ApiResponses<T>> created(SuccessCode code, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(code, data));
    }
    public static <T> ResponseEntity<ApiResponses<T>> created(SuccessCode code) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(code, null));
    }

    public static <T> ResponseEntity<ApiResponses<T>> error(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(fail(code));
    }
    public static <T> ResponseEntity<ApiResponses<T>> error(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(fail(code, message));
    }
}
