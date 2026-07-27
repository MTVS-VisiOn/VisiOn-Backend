package mtvs.onvision.vision.favorite.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.favorite.domain.Favorite;
import mtvs.onvision.vision.favorite.dto.FavoriteRequest;
import mtvs.onvision.vision.favorite.dto.FavoriteResponse;
import mtvs.onvision.vision.favorite.repository.FavoriteRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final UserService userService;

    @Transactional
    public void saveFavorite(@Valid FavoriteRequest request, CurrentUser currentUser) {
        PreConditions.check(favoriteRepository.existsByUserIdAndPkeyAndDeletedAtIsNull(currentUser.getId(), request.pkey()), ErrorCode.EXIST_FAVORITE);
        User user = userService.currentUserToUser(currentUser.getId());
        Favorite favorite = new Favorite(request, user);
        favoriteRepository.save(favorite);
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> searchFavorite(CurrentUser currentUser, String keyword) {
        Long userId = currentUser.getId();
        List<Favorite> favorites;
        if (keyword == null || keyword.isBlank()) favorites = favoriteRepository.findAllByUserIdAndDeletedAtIsNull(userId);
        else favorites = favoriteRepository.searchFavorite(userId, keyword);
        return favorites.stream().map(FavoriteResponse::from).toList();

    }
}
