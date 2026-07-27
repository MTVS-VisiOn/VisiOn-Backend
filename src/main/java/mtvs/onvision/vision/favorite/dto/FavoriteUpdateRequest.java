package mtvs.onvision.vision.favorite.dto;

import jakarta.validation.constraints.NotNull;

public record FavoriteUpdateRequest(
        @NotNull String nickname
) {
}
