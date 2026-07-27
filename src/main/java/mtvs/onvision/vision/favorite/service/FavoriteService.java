package mtvs.onvision.vision.favorite.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.favorite.domain.Favorite;
import mtvs.onvision.vision.favorite.dto.FavoriteRequest;
import mtvs.onvision.vision.favorite.repository.FavoriteRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
