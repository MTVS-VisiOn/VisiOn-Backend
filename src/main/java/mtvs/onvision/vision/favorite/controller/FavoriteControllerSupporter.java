package mtvs.onvision.vision.favorite.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.response.ApiResult;
import mtvs.onvision.vision.favorite.dto.FavoriteRequest;
import mtvs.onvision.vision.favorite.dto.FavoriteResponse;
import mtvs.onvision.vision.favorite.dto.FavoriteUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "favorite API", description = "즐겨찾기 API")
public interface FavoriteControllerSupporter {

    @Operation(
            summary = "피보호자 즐겨찾기 저장",
            description = "피보호자만 가능. 장소검색(GET /api/locations/search) 응답 항목을 그대로 담아 보낸다",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "1"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "저장 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "FAVORITE_CREATED",
                                                        "message": "즐겨찾기가 정상적으로 저장되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "필수값이 비었거나 길이 제한을 넘었을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "크기가 0에서 50 사이여야 합니다",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "같은 장소를 이미 저장했을때 (user_id + pkey 기준)",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "EXIST_FAVORITE",
                                                        "message": "이미 저장된 장소입니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> saveFavorite(FavoriteRequest request,
                                                 CurrentUser currentUser);

    @Operation(
            summary = "즐겨찾기 검색 / 전체 목록",
            description = """
                    피보호자와 보호자 모두 가능. 보호자가 호출하면 피보호자의 즐겨찾기를 조회한다.

                    `keyword`가 없으면 전체 목록(페이지당 10건), 있으면 검색 결과(페이지당 5건)를 준다.
                    검색은 별칭이 키워드와 일치하는 항목을 먼저 정렬한다.

                    `page`는 **1부터** 시작하지만, 응답의 `page.number`는 0부터 시작한다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "2"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(
                                                    name = "전체 목록 (keyword 없음)",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "FAVORITE_READ",
                                                                "message": "즐겨찾기가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "content": [
                                                                        {
                                                                            "id": 3,
                                                                            "name": "화목순대국 광화문1호점",
                                                                            "nickname": "점심집",
                                                                            "latitude": 37.57120358,
                                                                            "longitude": 126.97471568,
                                                                            "landAddress": "서울 종로구 당주동 40",
                                                                            "roadAddress": "서울 종로구 새문안로5길 11"
                                                                        },
                                                                        {
                                                                            "id": 1,
                                                                            "name": "왕남초등학교",
                                                                            "nickname": null,
                                                                            "latitude": 37.42850015,
                                                                            "longitude": 127.09801405,
                                                                            "landAddress": "경기 성남시 수정구 고등동 589",
                                                                            "roadAddress": "경기 성남시 수정구 왕남로 24"
                                                                        }
                                                                    ],
                                                                    "page": {
                                                                        "size": 10,
                                                                        "number": 0,
                                                                        "totalElements": 2,
                                                                        "totalPages": 1
                                                                    }
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "검색 (keyword 있음)",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "FAVORITE_READ",
                                                                "message": "즐겨찾기가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "content": [
                                                                        {
                                                                            "id": 3,
                                                                            "name": "화목순대국 광화문1호점",
                                                                            "nickname": "점심집",
                                                                            "latitude": 37.57120358,
                                                                            "longitude": 126.97471568,
                                                                            "landAddress": "서울 종로구 당주동 40",
                                                                            "roadAddress": "서울 종로구 새문안로5길 11"
                                                                        }
                                                                    ],
                                                                    "page": {
                                                                        "size": 5,
                                                                        "number": 0,
                                                                        "totalElements": 1,
                                                                        "totalPages": 1
                                                                    }
                                                                }
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "결과 없음",
                                                    value = """
                                                            {
                                                                "success": true,
                                                                "code": "FAVORITE_READ",
                                                                "message": "즐겨찾기가 정상적으로 조회되었습니다.",
                                                                "data": {
                                                                    "content": [],
                                                                    "page": {
                                                                        "size": 5,
                                                                        "number": 0,
                                                                        "totalElements": 0,
                                                                        "totalPages": 0
                                                                    }
                                                                }
                                                            }
                                                            """
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "page가 1보다 작거나 keyword가 30자를 넘었을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "page는 1 이상의 정수값입니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "보호자가 호출했는데 연결된 피보호자가 없을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_RELATION",
                                                        "message": "연결된 관계를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Page<FavoriteResponse>>> searchFavorite(
            CurrentUser currentUser,
            @Parameter(description = "검색 키워드. 없으면 전체 목록. 30자 이하", example = "순대")
            String keyword,
            @Parameter(description = "페이지 번호. 1부터 시작", example = "1")
            int page);

    @Operation(
            summary = "피보호자 즐겨찾기 별칭 수정",
            description = """
                    피보호자만 가능. 별칭(`nickname`)만 바꾼다.
                    장소명·좌표·주소는 티맵 소유 데이터라 수정 대상이 아니다.

                    `nickname`을 생략하거나 null, 공백으로 보내면 별칭이 삭제된다.
                    """,
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "3"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "FAVORITE_UPDATED",
                                                        "message": "즐겨찾기가 정상적으로 수정되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "별칭이 50자를 넘었을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "크기가 0에서 50 사이여야 합니다",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "없는 id이거나 다른 사용자의 즐겨찾기일때. 둘을 구분하지 않는다",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_FAVORITE",
                                                        "message": "해당 아이디의 즐겨찾기를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> updateFavorite(
            @Parameter(description = "즐겨찾기 id", example = "3", required = true)
            Long favoriteId,
            FavoriteUpdateRequest request,
            CurrentUser currentUser);

    @Operation(
            summary = "피보호자 즐겨찾기 삭제",
            description = "피보호자만 가능. 행을 지우지 않고 `deletedAt`만 채우는 소프트 삭제. 삭제한 장소는 다시 저장할 수 있다",
            extensions = @Extension(properties = @ExtensionProperty(name = "x-order", value = "4"))
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": true,
                                                        "code": "FAVORITE_DELETED",
                                                        "message": "즐겨찾기가 정상적으로 삭제되었습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 권한으로 실행했을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "ACCESS_DENIED",
                                                        "message": "권한이 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "없는 id이거나 다른 사용자의 즐겨찾기이거나 이미 삭제됐을때",
                    content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                        "success": false,
                                                        "code": "NOT_FOUND_FAVORITE",
                                                        "message": "해당 아이디의 즐겨찾기를 찾을 수 없습니다.",
                                                        "data": null
                                                    }
                                                    """
                                    )
                            )
                    }
            )
    })
    ResponseEntity<ApiResult<Void>> deleteFavorite(
            @Parameter(description = "즐겨찾기 id", example = "3", required = true)
            Long favoriteId,
            CurrentUser currentUser);
}
