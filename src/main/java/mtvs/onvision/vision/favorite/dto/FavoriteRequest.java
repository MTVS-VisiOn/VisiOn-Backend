package mtvs.onvision.vision.favorite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FavoriteRequest(
        @NotBlank
        @Size(max = 50)
        String pkey,
        @NotBlank
        @Size(max = 50)
        String name,

        @Size(max = 50)
        String nickname,
        @NotNull
        Double noorLat,
        @NotNull
        Double noorLon,
        @NotBlank
        @Size(max = 100)
        String landAddress,
        @NotBlank
        @Size(max = 100)
        String roadAddress
) {
}
