package mtvs.onvision.vision.favorite.dto;

import mtvs.onvision.vision.favorite.domain.Favorite;

public record FavoriteResponse(
        Long id,
        String name,
        String nickname,
        Double latitude,
        Double longitude,
        String landAddress,
        String roadAddress
) {
    public static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(favorite.getId(), favorite.getName(), favorite.getNickname(), favorite.getLatitude(), favorite.getLongitude(), favorite.getLandAddress(), favorite.getRoadAddress());
    }
}
