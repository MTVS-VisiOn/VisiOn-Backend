package mtvs.onvision.vision.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record FavoriteUpdateRequest(
        @Size(max = 50)
        @Schema(
                examples = "점심집",
                description = "바꿀 별칭. 생략하거나 null, 공백으로 보내면 별칭이 삭제된다"
        )
        String nickname
) {
}
