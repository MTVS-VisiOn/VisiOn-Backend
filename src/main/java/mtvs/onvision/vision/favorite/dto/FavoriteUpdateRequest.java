package mtvs.onvision.vision.favorite.dto;

import jakarta.validation.constraints.Size;

public record FavoriteUpdateRequest(
        @Size(max = 50)String nickname
) {
}
