package mtvs.onvision.vision.favorite.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.favorite.domain.Favorite;
import mtvs.onvision.vision.favorite.dto.FavoriteRequest;
import mtvs.onvision.vision.favorite.dto.FavoriteResponse;
import mtvs.onvision.vision.favorite.dto.FavoriteUpdateRequest;
import mtvs.onvision.vision.favorite.repository.FavoriteRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteService의")
class FavoriteServiceTest {

    @InjectMocks
    private FavoriteService favoriteService;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private UserService userService;

    Long guardianId = 1L;
    Long wardId = 2L;

    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);
    CurrentUser ward = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);

    User wardEntity = new User("ward@test.com", "password", "피보호자", "01012345678", UserRole.WARD);

    /** 장소검색 응답을 그대로 되던진 형태. nickname만 선택값이다 */
    private FavoriteRequest request(String nickname) {
        return new FavoriteRequest(
                "287479301",
                "화목순대국 광화문1호점",
                nickname,
                37.57120358,
                126.97471568,
                "서울 종로구 당주동 40",
                "서울 종로구 새문안로5길 11"
        );
    }

    private Favorite favorite(String name, String nickname) {
        FavoriteRequest request = new FavoriteRequest(
                "287479301", name, nickname, 37.5, 127.0, "지번주소", "도로명주소");
        return new Favorite(request, wardEntity);
    }

    /** 리포지토리에 실제로 넘어간 Pageable을 꺼낸다 */
    private Pageable captureListPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(favoriteRepository)
                .findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(anyLong(), captor.capture());
        return captor.getValue();
    }

    /** 리포지토리에 실제로 넘어간 like 패턴과 Pageable을 꺼낸다 */
    private ArgumentCaptor<String> captureSearchPattern() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(favoriteRepository).searchFavorite(anyLong(), captor.capture(), any());
        return captor;
    }

    @Nested
    @DisplayName("Describe: saveFavorite 메서드는")
    class Describe_with_saveFavorite {

        @Nested
        @DisplayName("Context: 아직 저장하지 않은 장소면")
        class Context_with_new_place {

            @Test
            @DisplayName("It : 검색 응답의 값을 그대로 복제해 저장한다")
            void it_saves_snapshot_of_request() {
                //given
                FavoriteRequest request = request("맛집");
                given(favoriteRepository.existsByUserIdAndPkeyAndDeletedAtIsNull(wardId, request.pkey()))
                        .willReturn(false);
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);

                //when
                favoriteService.saveFavorite(request, ward);

                //then
                ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
                verify(favoriteRepository).save(captor.capture());
                Favorite saved = captor.getValue();

                assertThat(saved.getPkey()).isEqualTo("287479301");
                assertThat(saved.getName()).isEqualTo("화목순대국 광화문1호점");
                assertThat(saved.getNickname()).isEqualTo("맛집");
                assertThat(saved.getLandAddress()).isEqualTo("서울 종로구 당주동 40");
                assertThat(saved.getRoadAddress()).isEqualTo("서울 종로구 새문안로5길 11");
                assertThat(saved.getUser()).isSameAs(wardEntity);
            }

            @Test
            @DisplayName("(noorLat/noorLon)It : 티맵 명칭을 latitude/longitude로 옮긴다")
            void it_maps_tmap_coordinate_names() {
                //given
                FavoriteRequest request = request(null);
                given(favoriteRepository.existsByUserIdAndPkeyAndDeletedAtIsNull(wardId, request.pkey()))
                        .willReturn(false);
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);

                //when
                favoriteService.saveFavorite(request, ward);

                //then
                ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
                verify(favoriteRepository).save(captor.capture());

                assertThat(captor.getValue().getLatitude()).isEqualTo(37.57120358);
                assertThat(captor.getValue().getLongitude()).isEqualTo(126.97471568);
            }

            @Test
            @DisplayName("(별칭 없이)It : nickname을 null로 저장한다")
            void it_allows_null_nickname() {
                //given
                FavoriteRequest request = request(null);
                given(favoriteRepository.existsByUserIdAndPkeyAndDeletedAtIsNull(wardId, request.pkey()))
                        .willReturn(false);
                given(userService.currentUserToUser(wardId)).willReturn(wardEntity);

                //when
                favoriteService.saveFavorite(request, ward);

                //then
                ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
                verify(favoriteRepository).save(captor.capture());

                assertThat(captor.getValue().getNickname()).isNull();
            }
        }

        @Nested
        @DisplayName("Context: 같은 pkey를 이미 저장했으면")
        class Context_with_duplicated_pkey {

            @Test
            @DisplayName("It : EXIST_FAVORITE 오류 발생")
            void it_throws_exist_favorite() {
                //given : 중복 판정 기준은 user_id + pkey
                FavoriteRequest request = request("맛집");
                given(favoriteRepository.existsByUserIdAndPkeyAndDeletedAtIsNull(wardId, request.pkey()))
                        .willReturn(true);

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> favoriteService.saveFavorite(request, ward));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXIST_FAVORITE);
            }

            @Test
            @DisplayName("It : 저장을 시도하지 않는다")
            void it_does_not_save() {
                //given
                FavoriteRequest request = request("맛집");
                given(favoriteRepository.existsByUserIdAndPkeyAndDeletedAtIsNull(wardId, request.pkey()))
                        .willReturn(true);

                //when
                assertThrows(BusinessException.class, () -> favoriteService.saveFavorite(request, ward));

                //then
                verify(favoriteRepository, never()).save(any());
            }
        }
    }

    @Nested
    @DisplayName("Describe: searchFavorite 메서드는")
    class Describe_with_searchFavorite {

        @Nested
        @DisplayName("Context: keyword가 없으면")
        class Context_without_keyword {

            @Test
            @DisplayName("It : 전체 목록을 페이지당 10건으로 조회한다")
            void it_reads_list_with_size_10() {
                //given
                given(favoriteRepository.findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(
                        eq(wardId), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(ward, null, 1);

                //then
                assertThat(captureListPageable().getPageSize()).isEqualTo(10);
            }

            @Test
            @DisplayName("(공백만 들어와도)It : 전체 목록 경로를 탄다")
            void it_treats_blank_keyword_as_list() {
                //given
                given(favoriteRepository.findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(
                        eq(wardId), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(ward, "   ", 1);

                //then
                verify(favoriteRepository, never()).searchFavorite(anyLong(), any(), any());
            }

            @Test
            @DisplayName("(요청은 1-based)It : page를 0-based로 변환해 넘긴다")
            void it_converts_page_to_zero_based() {
                //given
                given(favoriteRepository.findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(
                        eq(wardId), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(ward, null, 3);

                //then
                assertThat(captureListPageable().getPageNumber()).isEqualTo(2);
            }
        }

        @Nested
        @DisplayName("Context: keyword가 있으면")
        class Context_with_keyword {

            @Test
            @DisplayName("It : 검색 결과를 페이지당 5건으로 조회한다")
            void it_reads_search_with_size_5() {
                //given
                given(favoriteRepository.searchFavorite(eq(wardId), any(), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(ward, "순대", 1);

                //then
                ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
                verify(favoriteRepository).searchFavorite(anyLong(), any(), captor.capture());
                assertThat(captor.getValue().getPageSize()).isEqualTo(5);
            }

            @Test
            @DisplayName("It : 앞뒤에 %를 붙인 like 패턴으로 변환한다")
            void it_wraps_keyword_with_wildcards() {
                //given
                given(favoriteRepository.searchFavorite(eq(wardId), any(), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(ward, "순대", 1);

                //then
                assertThat(captureSearchPattern().getValue()).isEqualTo("%순대%");
            }

            @Test
            @DisplayName("(%와 _가 섞여 있으면)It : 와일드카드로 동작하지 않도록 이스케이프한다")
            void it_escapes_wildcard_characters() {
                //given : 이스케이프 문자는 '!' (쿼리의 escape '!'와 짝)
                given(favoriteRepository.searchFavorite(eq(wardId), any(), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(ward, "50%_할인", 1);

                //then
                assertThat(captureSearchPattern().getValue()).isEqualTo("%50!%!_할인%");
            }

            @Test
            @DisplayName("(이스케이프 문자 자신이 들어오면)It : 먼저 이중화한다")
            void it_escapes_the_escape_character_first() {
                //given : '!'를 나중에 치환하면 앞 단계가 만든 '!'까지 다시 이스케이프된다
                given(favoriteRepository.searchFavorite(eq(wardId), any(), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(ward, "대박!%", 1);

                //then
                assertThat(captureSearchPattern().getValue()).isEqualTo("%대박!!!%%");
            }
        }

        @Nested
        @DisplayName("Context: 호출자가 GUARDIAN이면")
        class Context_with_guardian {

            @Test
            @DisplayName("It : 본인이 아니라 피보호자의 즐겨찾기를 조회한다")
            void it_reads_ward_favorites() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(favoriteRepository.findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(
                        eq(wardId), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(guardian, null, 1);

                //then
                verify(favoriteRepository)
                        .findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(eq(wardId), any());
            }
        }

        @Nested
        @DisplayName("Context: 호출자가 WARD면")
        class Context_with_ward {

            @Test
            @DisplayName("It : 피보호자 조회를 거치지 않고 본인 id를 쓴다")
            void it_uses_own_id() {
                //given
                given(favoriteRepository.findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(
                        eq(wardId), any(Pageable.class)))
                        .willReturn(Page.empty());

                //when
                favoriteService.searchFavorite(ward, null, 1);

                //then
                verify(userService, never()).getWardIdFromGuardianId(anyLong());
            }
        }

        @Nested
        @DisplayName("Context: 조회 결과가 있으면")
        class Context_with_results {

            @Test
            @DisplayName("It : 페이지 정보를 유지한 채 FavoriteResponse로 변환한다")
            void it_maps_to_response_keeping_page_metadata() {
                //given
                Page<Favorite> found = new PageImpl<>(
                        List.of(favorite("화목순대국", "맛집"), favorite("용남초등학교", null)),
                        PageRequest.of(0, 10),
                        12);
                given(favoriteRepository.findByUserIdAndDeletedAtIsNullOrderByNicknameAscNameAsc(
                        eq(wardId), any(Pageable.class)))
                        .willReturn(found);

                //when
                Page<FavoriteResponse> response = favoriteService.searchFavorite(ward, null, 1);

                //then
                assertThat(response.getTotalElements()).isEqualTo(12);
                assertThat(response.getTotalPages()).isEqualTo(2);
                assertThat(response.getContent()).hasSize(2);
                assertThat(response.getContent().getFirst().name()).isEqualTo("화목순대국");
                assertThat(response.getContent().getFirst().nickname()).isEqualTo("맛집");
                assertThat(response.getContent().get(1).nickname()).isNull();
            }
        }
    }

    @Nested
    @DisplayName("Describe: updateFavorite 메서드는")
    class Describe_with_updateFavorite {

        @Nested
        @DisplayName("Context: 본인 소유의 즐겨찾기면")
        class Context_with_own_favorite {

            @Test
            @DisplayName("It : nickname만 바꾼다")
            void it_updates_nickname_only() {
                //given
                Favorite favorite = favorite("화목순대국", "맛집");
                given(favoriteRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, wardId))
                        .willReturn(Optional.of(favorite));

                //when
                favoriteService.updateFavorite(10L, ward, new FavoriteUpdateRequest("점심집"));

                //then
                assertThat(favorite.getNickname()).isEqualTo("점심집");
                assertThat(favorite.getName()).isEqualTo("화목순대국");
                assertThat(favorite.getLatitude()).isEqualTo(37.5);
            }
        }

        @Nested
        @DisplayName("Context: 없는 id이거나 남의 즐겨찾기면")
        class Context_with_others_favorite {

            @Test
            @DisplayName("It : 403이 아니라 NOT_FOUND_FAVORITE 오류 발생")
            void it_throws_not_found_favorite() {
                //given : 소유권을 조회 조건에 넣어 id 존재 여부를 노출하지 않는다
                given(favoriteRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, wardId))
                        .willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> favoriteService.updateFavorite(10L, ward, new FavoriteUpdateRequest("점심집")));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_FAVORITE);
            }
        }
    }

    @Nested
    @DisplayName("Describe: deleteFavorite 메서드는")
    class Describe_with_deleteFavorite {

        @Nested
        @DisplayName("Context: 본인 소유의 즐겨찾기면")
        class Context_with_own_favorite {

            @Test
            @DisplayName("It : 행을 지우지 않고 deletedAt만 채운다")
            void it_soft_deletes() {
                //given
                Favorite favorite = favorite("화목순대국", "맛집");
                given(favoriteRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, wardId))
                        .willReturn(Optional.of(favorite));

                //when
                favoriteService.deleteFavorite(10L, ward);

                //then
                assertThat(favorite.getDeletedAt()).isNotNull();
                verify(favoriteRepository, never()).delete(any());
            }
        }

        @Nested
        @DisplayName("Context: 없는 id이거나 이미 삭제된 즐겨찾기면")
        class Context_with_deleted_favorite {

            @Test
            @DisplayName("It : NOT_FOUND_FAVORITE 오류 발생")
            void it_throws_not_found_favorite() {
                //given : DeletedAtIsNull 조건이 걸려 있어 삭제된 행은 조회되지 않는다
                given(favoriteRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, wardId))
                        .willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> favoriteService.deleteFavorite(10L, ward));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_FAVORITE);
            }
        }
    }
}
