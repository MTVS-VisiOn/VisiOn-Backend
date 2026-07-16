package mtvs.onvision.vision.common.response;

public record ApiResponses<T>(
        boolean success,
        String code,
        String message,
        T data
){
}