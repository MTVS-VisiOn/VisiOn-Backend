package mtvs.onvision.vision.favorite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FavoriteRequest(
        @NotBlank
        String pkey,
        @NotBlank
        String name,

        String nickname,
        @NotNull
        Double noorLat,
        @NotNull
        Double noorLon,
        @NotBlank
        String landAddress,
        @NotBlank
        String roadAddress
) {
}
