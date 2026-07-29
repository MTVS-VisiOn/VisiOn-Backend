package mtvs.onvision.vision.navigation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LocationInfo(
        @NotBlank
        @Size(max = 50)
        String name,

        @Size(max = 50)
        @NotNull
        String nickname,

        @NotNull
        Double latitude,

        @NotNull
        Double longitude,

        @NotBlank
        @Size(max = 100)
        String roadAddress,

        Long favoriteId
) {
}
