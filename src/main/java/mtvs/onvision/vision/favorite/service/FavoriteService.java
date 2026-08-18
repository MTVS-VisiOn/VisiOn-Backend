package mtvs.onvision.vision.favorite.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
        log.debug("Favorite saved: favoriteId={} userId={} pkey={} name={}",
                favorite.getId(), currentUser.getId(), request.pkey(), request.name());
    }

    @Transactional(readOnly = true)
    public Page<FavoriteResponse> searchFavorite(CurrentUser currentUser, String keyword, int page) {
        UserRole role = currentUser.getRole();
        Long userId = role == UserRole.GUARDIAN? userService.getWardIdFromGuardianId(currentUser.getId()) : currentUser.getId();
        log.debug("Favorite search requested: requesterId={} role={} 조회대상userId={} keyword={} page={}",
                currentUser.getId(), role, userId, keyword, page);
        Page<Favorite> favorites;
        Pageable pageable;

        if (keyword == null || keyword.isBlank()) {
            pageable = PageRequest.of(page-1, 10);
            favorites = favoriteRepository.findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(userId, pageable);
        }
        else  {
            pageable = PageRequest.of(page-1, 5);
            favorites = favoriteRepository.searchFavorite(userId, toLikePattern(keyword), pageable);
        }
        log.debug("Favorite search done: userId={} totalElements={} returned={} pageSize={}",
                userId, favorites.getTotalElements(), favorites.getNumberOfElements(), pageable.getPageSize());
        return favorites.map(FavoriteResponse::from);
    }

    //즐겨찾기 닉네임 바꾸기
    @Transactional
    public void updateFavorite(Long favoriteId, CurrentUser currentUser, FavoriteUpdateRequest request) {
        Favorite favorite = favoriteRepository.findByIdAndUserIdAndDeletedAtIsNull(favoriteId, currentUser.getId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_FAVORITE));
        favorite.update(request.nickname() == null || request.nickname().isBlank() ? null : request.nickname());
        log.debug("Favorite updated: favoriteId={} userId={} nickname={}", favoriteId, currentUser.getId(), request.nickname());
    }

    @Transactional
    public void deleteFavorite(Long favoriteId, CurrentUser currentUser) {
        Favorite favorite = favoriteRepository.findByIdAndUserIdAndDeletedAtIsNull(favoriteId, currentUser.getId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_FAVORITE));
        favorite.delete();
        log.debug("Favorite deleted: favoriteId={} userId={}", favoriteId, currentUser.getId());
    }

    private String toLikePattern(String keyword) {
        String escaped = keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
