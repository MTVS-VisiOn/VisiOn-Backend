package mtvs.onvision.vision.common.util;

import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;

public class PreConditions {
    public static void check(boolean expression, ErrorCode errorCode){
        if (expression) throw new BusinessException(errorCode);
    }
    public static void check(boolean expression, ErrorCode errorCode, String message){
        if (expression) throw new BusinessException(errorCode, message);
    }
}
