package mtvs.onvision.vision.favorite.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.favorite.dto.FavoriteRequest;
import mtvs.onvision.vision.favorite.dto.FavoriteResponse;
import mtvs.onvision.vision.favorite.dto.FavoriteUpdateRequest;
import mtvs.onvision.vision.favorite.service.FavoriteService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<ApiResult<Void>> saveFavorite(@RequestBody @Valid FavoriteRequest request,
                                                        @AuthenticationPrincipal CurrentUser currentUser) {
        favoriteService.saveFavorite(request, currentUser);
        return ApiResult.created(SuccessCode.FAVORITE_CREATED);
    }

    //즐겨찾기 검색
    @GetMapping("/search")
    public ResponseEntity<ApiResult<Page<FavoriteResponse>>> searchFavorite(@AuthenticationPrincipal CurrentUser currentUser,
                                                                            @RequestParam(required = false) String keyword,
                                                                            @RequestParam(required = false, defaultValue = "1") int page) {
        Page<FavoriteResponse> response = favoriteService.searchFavorite(currentUser, keyword, page);

        return ApiResult.ok(SuccessCode.FAVORITE_READ, response);
    }

    //즐겨찾기 수정
    @PatchMapping("/{favoriteId}")
    public ResponseEntity<ApiResult<Void>> updateFavorite(@PathVariable Long favoriteId,
                                                          @RequestBody @Valid FavoriteUpdateRequest request,
                                                          @AuthenticationPrincipal CurrentUser currentUser) {
        favoriteService.updateFavorite(favoriteId, currentUser, request);
        return ApiResult.ok(SuccessCode.FAVORITE_UPDATED);
    }

    //즐겨찾기 삭제
    @DeleteMapping("/{favoriteId}")
    public ResponseEntity<ApiResult<Void>> deleteFavorite(@PathVariable Long favoriteId,
                                                          @AuthenticationPrincipal CurrentUser currentUser) {
        favoriteService.deleteFavorite(favoriteId, currentUser);
        return ApiResult.ok(SuccessCode.FAVORITE_DELETED);
    }


}
