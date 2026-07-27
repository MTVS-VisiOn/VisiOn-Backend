package mtvs.onvision.vision.location.dto;

import java.util.List;

public record LocationSearchResponse(
        Integer totalCount,
        Integer count,
        Integer page,
        List<LocationSearchInfo> infos
) {
}
