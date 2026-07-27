package mtvs.onvision.vision.favorite.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.favorite.domain.Favorite;
import mtvs.onvision.vision.favorite.dto.FavoriteRequest;
import mtvs.onvision.vision.favorite.dto.FavoriteResponse;
import mtvs.onvision.vision.favorite.dto.FavoriteUpdateRequest;
import mtvs.onvision.vision.favorite.repository.FavoriteRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
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
    public void saveFavorite(FavoriteRequest request, CurrentUser currentUser) {
        PreConditions.check(favoriteRepository.existsByUserIdAndPkeyAndDeletedAtIsNull(currentUser.getId(), request.pkey()), ErrorCode.EXIST_FAVORITE);
        User user = userService.currentUserToUser(currentUser.getId());
        Favorite favorite = new Favorite(request, user);
        favoriteRepository.save(favorite);
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> searchFavorite(CurrentUser currentUser, String keyword) {
        UserRole role = currentUser.getRole();
        Long userId = role == UserRole.GUARDIAN? userService.getWardIdFromGuardianId(currentUser.getId()) : currentUser.getId();
        List<Favorite> favorites;
        if (keyword == null || keyword.isBlank()) favorites = favoriteRepository.findTop5ByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(userId);
        else favorites = favoriteRepository.searchFavorite(userId, keyword);
        return favorites.stream().map(FavoriteResponse::from).toList();
    }

    //즐겨찾기 닉네임 바꾸기
    @Transactional
    public void updateFavorite(Long favoriteId, CurrentUser currentUser, FavoriteUpdateRequest request) {
        Favorite favorite = favoriteRepository.findByIdAndUserIdAndDeletedAtIsNull(favoriteId, currentUser.getId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_FAVORITE));
        favorite.update(request.nickname());
    }

    @Transactional
    public void deleteFavorite(Long favoriteId, CurrentUser currentUser) {
        Favorite favorite = favoriteRepository.findByIdAndUserIdAndDeletedAtIsNull(favoriteId, currentUser.getId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_FAVORITE));
        favorite.delete();
    }
}
