package mtvs.onvision.vision.favorite.controller;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.entrypoint.JwtAccessDeniedHandler;
import mtvs.onvision.vision.common.entrypoint.JwtAuthenticationEntryPoint;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.filter.JwtAuthenticationFilter;
import mtvs.onvision.vision.common.response.SuccessCode;
import mtvs.onvision.vision.favorite.dto.FavoriteRequest;
import mtvs.onvision.vision.favorite.dto.FavoriteResponse;
import mtvs.onvision.vision.favorite.dto.FavoriteUpdateRequest;
import mtvs.onvision.vision.favorite.service.FavoriteService;
import mtvs.onvision.vision.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoriteController.class)
@AutoConfigureMockMvc(addFilters = false)
class FavoriteControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    private FavoriteService favoriteService;

    @MockitoBean
    JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @MockitoBean
    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    Long wardId = 2L;
    CurrentUser currentUser = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 장소검색 응답을 그대로 되던진 형태 */
    private FavoriteRequest request(String name) {
        return new FavoriteRequest(
                "287479301",
                name,
                "맛집",
                37.57120358,
                126.97471568,
                "서울 종로구 당주동 40",
                "서울 종로구 새문안로5길 11"
        );
    }

    private Page<FavoriteResponse> page(FavoriteResponse... responses) {
        return new PageImpl<>(List.of(responses), PageRequest.of(0, 10), responses.length);
    }

    @Nested
    @DisplayName("Describe: POST /api/favorites 엔드포인트는")
    class saveFavorite {

        @Nested
        @DisplayName("Context: 검색 응답을 그대로 되던지면")
        class Context_with_valid_request {

            @Test
            @DisplayName("It : 201 상태와 성공 메시지를 반환한다")
            void it_return_201_created() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/favorites")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request("화목순대국")))
                        )
                        .andExpect(status().isCreated())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.code").value(SuccessCode.FAVORITE_CREATED.name()))
                        .andExpect(jsonPath("$.message").value(SuccessCode.FAVORITE_CREATED.getSuccessMessage()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 필수값이 비어 있으면")
        class Context_with_blank_required_field {

            @Test
            @DisplayName("It : 400 상태와 VALIDATION_FAILED를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //when-then
                mockMvc.perform(
                                post("/api/favorites")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request("")))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 이미 저장한 장소면")
        class Context_with_duplicated_place {

            @Test
            @DisplayName("It : 409 상태와 EXIST_FAVORITE를 반환한다")
            void it_return_409_conflict() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.EXIST_FAVORITE))
                        .when(favoriteService).saveFavorite(any(), any());

                //when-then
                mockMvc.perform(
                                post("/api/favorites")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request("화목순대국")))
                        )
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(ErrorCode.EXIST_FAVORITE.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: GET /api/favorites/search 엔드포인트는")
    class searchFavorite {

        @Nested
        @DisplayName("Context: keyword 없이 호출하면")
        class Context_without_keyword {

            @Test
            @DisplayName("It : 200 상태와 즐겨찾기 목록을 반환한다")
            void it_return_200_with_content() throws Exception {
                //given
                given(favoriteService.searchFavorite(any(), isNull(), eq(1)))
                        .willReturn(page(new FavoriteResponse(
                                1L, "화목순대국", "맛집", 37.5, 127.0, "지번주소", "도로명주소")));

                //when-then : VIA_DTO든 아니든 content 배열은 동일하다
                mockMvc.perform(get("/api/favorites/search"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value(SuccessCode.FAVORITE_READ.name()))
                        .andExpect(jsonPath("$.data.content[0].id").value(1))
                        .andExpect(jsonPath("$.data.content[0].name").value("화목순대국"))
                        .andExpect(jsonPath("$.data.content[0].nickname").value("맛집"))
                        .andDo(print());
            }

            @Test
            @DisplayName("(page를 안 보내면)It : 1페이지로 조회한다")
            void it_defaults_page_to_1() throws Exception {
                //given
                given(favoriteService.searchFavorite(any(), isNull(), eq(1))).willReturn(page());

                //when
                mockMvc.perform(get("/api/favorites/search")).andExpect(status().isOk());

                //then
                verify(favoriteService).searchFavorite(any(), isNull(), eq(1));
            }
        }

        @Nested
        @DisplayName("Context: keyword와 page를 함께 보내면")
        class Context_with_keyword_and_page {

            @Test
            @DisplayName("It : 받은 값을 그대로 서비스에 넘긴다")
            void it_passes_keyword_and_page() throws Exception {
                //given
                given(favoriteService.searchFavorite(any(), eq("순대"), eq(3))).willReturn(page());

                //when
                mockMvc.perform(get("/api/favorites/search")
                                .param("keyword", "순대")
                                .param("page", "3"))
                        .andExpect(status().isOk());

                //then
                verify(favoriteService).searchFavorite(any(), eq("순대"), eq(3));
            }
        }
    }

    @Nested
    @DisplayName("Describe: PATCH /api/favorites/{favoriteId} 엔드포인트는")
    class updateFavorite {

        @Nested
        @DisplayName("Context: 본인 소유의 즐겨찾기면")
        class Context_with_own_favorite {

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok() throws Exception {
                //when-then
                mockMvc.perform(
                                patch("/api/favorites/10")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new FavoriteUpdateRequest("점심집")))
                        )
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.FAVORITE_UPDATED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 없는 id이거나 남의 즐겨찾기면")
        class Context_with_others_favorite {

            @Test
            @DisplayName("It : 403이 아니라 404 상태를 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_FAVORITE))
                        .when(favoriteService).updateFavorite(eq(10L), any(), any());

                //when-then
                mockMvc.perform(
                                patch("/api/favorites/10")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(new FavoriteUpdateRequest("점심집")))
                        )
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_FAVORITE.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: nickname을 생략하면")
        class Context_without_nickname {

            @Test
            @DisplayName("It : 별칭 삭제 요청으로 보고 200 상태를 반환한다")
            void it_return_200_ok() throws Exception {
                //when-then : @NotNull을 빼서 null이 유효한 값이 되었다
                mockMvc.perform(
                                patch("/api/favorites/10")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")
                        )
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.FAVORITE_UPDATED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: nickname이 50자를 넘으면")
        class Context_with_too_long_nickname {

            @Test
            @DisplayName("It : 400 상태와 VALIDATION_FAILED를 반환한다")
            void it_return_400_bad_request() throws Exception {
                //given : 엔티티가 length = 50이라 DTO에서 걸러야 500이 아닌 400이 된다
                FavoriteUpdateRequest request = new FavoriteUpdateRequest("가".repeat(51));

                //when-then
                mockMvc.perform(
                                patch("/api/favorites/10")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(om.writeValueAsString(request))
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("Describe: DELETE /api/favorites/{favoriteId} 엔드포인트는")
    class deleteFavorite {

        @Nested
        @DisplayName("Context: 본인 소유의 즐겨찾기면")
        class Context_with_own_favorite {

            @Test
            @DisplayName("It : 200 상태와 성공 메시지를 반환한다")
            void it_return_200_ok() throws Exception {
                //when-then
                mockMvc.perform(delete("/api/favorites/10").with(csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(SuccessCode.FAVORITE_DELETED.name()))
                        .andDo(print());
            }
        }

        @Nested
        @DisplayName("Context: 없는 id이거나 이미 삭제된 즐겨찾기면")
        class Context_with_deleted_favorite {

            @Test
            @DisplayName("It : 404 상태를 반환한다")
            void it_return_404_not_found() throws Exception {
                //given
                doThrow(new BusinessException(ErrorCode.NOT_FOUND_FAVORITE))
                        .when(favoriteService).deleteFavorite(eq(10L), any());

                //when-then
                mockMvc.perform(delete("/api/favorites/10").with(csrf()))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND_FAVORITE.name()))
                        .andDo(print());
            }
        }
    }
}
