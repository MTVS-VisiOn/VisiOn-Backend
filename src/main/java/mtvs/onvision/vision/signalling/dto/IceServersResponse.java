package mtvs.onvision.vision.signalling.dto;

import java.util.List;

public record IceServersResponse(
        List<IceServer> iceServers
) {
}
