package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;

import java.util.Arrays;

@RequiredArgsConstructor
public enum GeometryType {
    POINT("Point"),
    LINE_STRING("LineString");


    private final String value;

    @JsonValue
    public String getValue() {return value;}

    @JsonCreator
    public static GeometryType from(String value) {
        return Arrays.stream(values())
                .filter(t -> t.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TMAP_API_ERROR));
    }
}
