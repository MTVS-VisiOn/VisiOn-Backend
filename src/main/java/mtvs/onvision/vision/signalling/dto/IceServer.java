package mtvs.onvision.vision.signalling.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IceServer(
        List<String> urls,
        String username,
        String credential
) {
    public static IceServer stun(List<String> urls) {
        return new IceServer(urls, null, null);
    }
}