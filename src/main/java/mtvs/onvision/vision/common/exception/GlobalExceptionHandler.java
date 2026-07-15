package mtvs.onvision.vision.common.exception;

import mtvs.onvision.vision.common.response.ApiResponses;
import mtvs.onvision.vision.common.response.ApiResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponses<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode code = exception.getErrorCode();
        return ApiResult.error(code);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponses<Void>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String errorMessage = exception.getBindingResult().getFieldError().getDefaultMessage();
        return ApiResult.error(ErrorCode.VALIDATION_FAILED, errorMessage);

    }
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponses<Void>> handleMissingRequestParam(
            MissingServletRequestParameterException e
    ) {
        String errorMessage = ErrorCode.REQUESTPARAM_REQUIRED.getMessage() + e.getParameterName();
        return ApiResult.error(ErrorCode.REQUESTPARAM_REQUIRED, errorMessage);
    }
}
