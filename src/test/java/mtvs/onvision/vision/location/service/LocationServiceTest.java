package mtvs.onvision.vision.location.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.location.domain.MovementStatus;
import mtvs.onvision.vision.location.dto.*;
import mtvs.onvision.vision.location.repository.LocationHistoryRepository;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
import mtvs.onvision.vision.presence.service.PresenceService;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationService의")
class LocationServiceTest {

    @Mock
    private LocationHistoryRepository locationHistoryRepository;

    @Mock
    private RealtimeLocationRepository realtimeLocationRepository;

    @Mock
    private PresenceService presenceService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectMapper objectMapper;

    private LocationService locationService;
    private MockRestServiceServer tmapServer;

    static final String BASE_URL = "https://apis.openapi.sk.com";

    Long guardianId = 1L;
    Long wardId = 2L;

    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);
    CurrentUser ward = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);

    /** 티맵 역지오코딩 응답. fullAddress는 마지막 콤마 뒤가 도로명 주소 */
    static final String TMAP_RESPONSE = """
            {
              "addressInfo": {
                "fullAddress": "경기도 부천시 원미구 심곡동 354-2,경기도 부천시 원미구 부일로 123",
                "addressType": "A10",
                "city_do": "경기도",
                "gu_gun": "부천시 원미구",
                "legalDong": "심곡동",
                "bunji": "354-2"
              }
            }
            """;

    @BeforeEach
    void setUp() {
        // RestClient는 fluent 체인이라 mock 대신 MockRestServiceServer로 실제 요청/응답을 검증한다
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        tmapServer = MockRestServiceServer.bindTo(builder).build();
        RestClient tmapRestClient = builder.build();

        locationService = new LocationService(
                locationHistoryRepository,
                realtimeLocationRepository,
                presenceService,
                userService,
                objectMapper,
                tmapRestClient
        );
    }

    private LocationRequest request(Double lat, Double lon, Float accuracy, Instant recordedAt) {
        return new LocationRequest(lat, lon, accuracy, recordedAt);
    }

    private LocationReport report(Double lat, Double lon, Float accuracy, MovementStatus status, Instant recordedAt) {
        return new LocationReport(wardId, lat, lon, accuracy, status, recordedAt);
    }

    /** receiveLocation이 저장하려고 만든 LocationReport를 꺼낸다 */
    private LocationReport captureSavedReport() {
        ArgumentCaptor<LocationReport> captor = ArgumentCaptor.forClass(LocationReport.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Describe: receiveLocation 메서드는")
    class Describe_with_receiveLocation {

        @Nested
        @DisplayName("Context: 이전 위치가 없으면")
        class Context_with_no_previous {

            @Test
            @DisplayName("It : UNKNOWN으로 판별해 저장한다")
            void it_classify_unknown() {
                //given
                LocationRequest request = request(37.5, 127.0, 10f, Instant.now());
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when
                locationService.receiveLocation(request, ward);

                //then
                assertThat(captureSavedReport().status()).isEqualTo(MovementStatus.UNKNOWN);
            }

            @Test
            @DisplayName("It : 판정과 무관하게 저장은 수행한다")
            void it_saves_anyway() {
                //given
                LocationRequest request = request(37.5, 127.0, 10f, Instant.now());
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when
                locationService.receiveLocation(request, ward);

                //then
                verify(realtimeLocationRepository).saveLocation(org.mockito.ArgumentMatchers.eq(wardId),
                        org.mockito.ArgumentMatchers.any());
            }
        }

        @Nested
        @DisplayName("Context: 이동 거리가 오차 반경 안이면")
        class Context_with_jitter {

            @Test
            @DisplayName("It : 지터로 보고 STATIONARY로 판별한다")
            void it_classify_stationary() {
                //given
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                // 위도 0.0001도 ≈ 11m 이동, 오차 반경 합은 40m
                LocationReport previous = report(37.5, 127.0, 20f, MovementStatus.STATIONARY, now.minusSeconds(30));
                LocationRequest request = request(37.5001, 127.0, 20f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                assertThat(captureSavedReport().status()).isEqualTo(MovementStatus.STATIONARY);
            }
        }

        @Nested
        @DisplayName("Context: 오차 반경 밖이지만 0.5 m/s 미만으로 이동했으면")
        class Context_with_very_slow_movement {

            @Test
            @DisplayName("It : STATIONARY로 판별한다")
            void it_classify_stationary() {
                //given
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                // 위도 0.0001도 ≈ 11m, 60초 → 약 0.19 m/s. 오차 반경 합은 0m라 지터 판정에 걸리지 않는다
                LocationReport previous = report(37.5, 127.0, 0f, MovementStatus.STATIONARY, now.minusSeconds(60));
                LocationRequest request = request(37.5001, 127.0, 0f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                assertThat(captureSavedReport().status()).isEqualTo(MovementStatus.STATIONARY);
            }
        }

        @Nested
        @DisplayName("Context: 오차 반경 밖으로 걷는 속도만큼 이동했으면")
        class Context_with_walking_distance {

            @Test
            @DisplayName("It : ON_FOOT으로 판별한다")
            void it_classify_on_foot() {
                //given
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                // 위도 0.0004도 ≈ 44m, 30초 → 약 1.48 m/s. 오차 반경 합은 10m
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.STATIONARY, now.minusSeconds(30));
                LocationRequest request = request(37.5004, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                assertThat(captureSavedReport().status()).isEqualTo(MovementStatus.ON_FOOT);
            }
        }

        @Nested
        @DisplayName("Context: 차량 속도만큼 이동했으면")
        class Context_with_vehicle_distance {

            @Test
            @DisplayName("It : IN_VEHICLE로 판별한다")
            void it_classify_in_vehicle() {
                //given
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                // 위도 0.01도 ≈ 1111m, 30초 → 약 37 m/s
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.STATIONARY, now.minusSeconds(30));
                LocationRequest request = request(37.51, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                assertThat(captureSavedReport().status()).isEqualTo(MovementStatus.IN_VEHICLE);
            }
        }

        @Nested
        @DisplayName("Context: 이전 위치와의 간격이 5분을 넘으면")
        class Context_with_stale_previous {

            @Test
            @DisplayName("It : UNKNOWN으로 판별한다")
            void it_classify_unknown() {
                //given
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.STATIONARY, now.minusSeconds(400));
                LocationRequest request = request(37.51, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                assertThat(captureSavedReport().status()).isEqualTo(MovementStatus.UNKNOWN);
            }
        }
    }

    @Nested
    @DisplayName("Describe: getLastLocation 메서드는")
    class Describe_with_getLastLocation {

        @Nested
        @DisplayName("Context: 저장된 최근 위치가 없으면")
        class Context_with_no_last_location {

            @Test
            @DisplayName("It : 예외 없이 null을 반환하고 티맵을 호출하지 않는다")
            void it_returns_null() {
                //given : 아직 한 번도 안 보냈거나 TTL로 사라진 경우는 오류가 아니다
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when
                LastLocationResponse response = locationService.getLastLocation(guardian);

                //then
                assertThat(response).isNull();
                tmapServer.verify();  // 호출된 요청 없음
            }
        }

        @Nested
        @DisplayName("Context: 최근 위치가 존재하면")
        class Context_with_available_last_location {

            @Test
            @DisplayName("It : 좌표로 티맵을 호출해 도로명 주소와 이동 상태를 반환한다")
            void it_return_address_and_status() {
                //given
                String latestJson = "{\"latest\":true}";
                LocationReport latest = new LocationReport(
                        wardId, 37.5, 127.0, 10f, MovementStatus.ON_FOOT, Instant.now());

                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(latestJson));
                given(objectMapper.readValue(latestJson, LocationReport.class)).willReturn(latest);

                tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/geo/reversegeocoding")))
                        .andExpect(queryParam("lat", "37.5"))
                        .andExpect(queryParam("lon", "127.0"))
                        .andExpect(queryParam("addressType", "A10"))
                        .andRespond(withSuccess(TMAP_RESPONSE, MediaType.APPLICATION_JSON));

                //when
                LastLocationResponse response = locationService.getLastLocation(guardian);

                //then
                assertThat(response.isConnected()).isTrue();
                assertThat(response.lastAddress()).isEqualTo("경기도 부천시 원미구 부일로 123");
                assertThat(response.status()).isEqualTo(MovementStatus.ON_FOOT.getMessage());
                tmapServer.verify();
            }

            @Test
            @DisplayName("It : presence를 조회하지 않는다")
            void it_does_not_consult_presence() {
                //given : presence 게이트 제거(2026-08-04) 회귀 방지
                String latestJson = "{\"latest\":true}";
                LocationReport latest = new LocationReport(
                        wardId, 37.5, 127.0, 10f, MovementStatus.ON_FOOT, Instant.now());

                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(latestJson));
                given(objectMapper.readValue(latestJson, LocationReport.class)).willReturn(latest);

                tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/geo/reversegeocoding")))
                        .andRespond(withSuccess(TMAP_RESPONSE, MediaType.APPLICATION_JSON));

                //when
                locationService.getLastLocation(guardian);

                //then
                verify(presenceService, org.mockito.Mockito.never()).getIsConnected(wardId);
            }
        }
    }

    @Nested
    @DisplayName("Describe: searchLocation 메서드는")
    class Describe_with_searchLocation {

        /** 부번이 "0"인 POI와 "14"인 POI를 함께 담았다 */
        static final String POI_SEARCH_RESPONSE = """
                {
                  "searchPoiInfo": {
                    "totalCount": "2",
                    "count": "2",
                    "page": "1",
                    "pois": {
                      "poi": [
                        {
                          "id": "2874793",
                          "pkey": "287479301",
                          "name": "화목순대국 광화문1호점",
                          "telNo": "02-723-8313",
                          "noorLat": "37.57120358",
                          "noorLon": "126.97471568",
                          "upperAddrName": "서울",
                          "middleAddrName": "종로구",
                          "lowerAddrName": "당주동",
                          "detailAddrName": "",
                          "firstNo": "40",
                          "secondNo": "0",
                          "newAddressList": {
                            "newAddress": [
                              { "fullAddressRoad": "서울 종로구 새문안로5길 11" }
                            ]
                          }
                        },
                        {
                          "id": "1561691",
                          "pkey": "156169101",
                          "name": "화목순대국",
                          "telNo": "02-780-8191",
                          "noorLat": "37.51934772",
                          "noorLon": "126.93149886",
                          "upperAddrName": "서울",
                          "middleAddrName": "영등포구",
                          "lowerAddrName": "여의도동",
                          "detailAddrName": "",
                          "firstNo": "44",
                          "secondNo": "14",
                          "newAddressList": {
                            "newAddress": [
                              { "fullAddressRoad": "서울 영등포구 여의대방로 383" }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        private void expectPoiSearch() {
            tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/pois")))
                    .andExpect(queryParam("version", "1"))
                    .andExpect(queryParam("count", "10"))
                    .andRespond(withSuccess(POI_SEARCH_RESPONSE, MediaType.APPLICATION_JSON));
        }

        @Nested
        @DisplayName("Context: 검색 결과가 존재하면")
        class Context_with_results {

            @Test
            @DisplayName("It : 페이지 정보와 장소 목록을 반환한다")
            void it_returns_places() {
                //given
                expectPoiSearch();

                //when
                LocationSearchResponse response = locationService.searchLocation("화목순대국");

                //then
                assertThat(response.totalCount()).isEqualTo(2);
                assertThat(response.count()).isEqualTo(2);
                assertThat(response.page()).isEqualTo(1);
                assertThat(response.infos().size()).isEqualTo(2);
                tmapServer.verify();
            }

            @Test
            @DisplayName("It : 중심점 좌표(noorLat/noorLon)와 도로명 주소를 매핑한다")
            void it_maps_center_coordinate_and_road_address() {
                //given
                expectPoiSearch();

                //when
                LocationSearchInfo first = locationService.searchLocation("화목순대국").infos().getFirst();

                //then
                assertThat(first.id()).isEqualTo("2874793");
                assertThat(first.pkey()).isEqualTo("287479301");
                assertThat(first.name()).isEqualTo("화목순대국 광화문1호점");
                assertThat(first.noorLat()).isEqualTo(37.57120358);
                assertThat(first.noorLon()).isEqualTo(126.97471568);
                assertThat(first.roadAddress()).isEqualTo("서울 종로구 새문안로5길 11");
            }

            @Test
            @DisplayName("(부번이 0)It : 지번 주소에 부번을 붙이지 않는다")
            void it_omits_zero_second_no() {
                //given
                expectPoiSearch();

                //when
                LocationSearchInfo first = locationService.searchLocation("화목순대국").infos().getFirst();

                //then : detailAddrName이 빈 값이라 뒤에 공백도 남지 않는다
                assertThat(first.landAddress()).isEqualTo("서울 종로구 당주동 40");
            }

            @Test
            @DisplayName("(부번이 있음)It : 지번 주소를 본번-부번으로 조합한다")
            void it_joins_first_and_second_no() {
                //given
                expectPoiSearch();

                //when
                LocationSearchInfo second = locationService.searchLocation("화목순대국").infos().get(1);

                //then
                assertThat(second.landAddress()).isEqualTo("서울 영등포구 여의도동 44-14");
            }
        }

        @Nested
        @DisplayName("Context: 키워드에 한글과 공백이 섞여 있으면")
        class Context_with_korean_keyword {

            @Test
            @DisplayName("It : 이중 인코딩 없이 한 번만 인코딩해 요청한다")
            void it_encodes_keyword_once() {
                //given : 이중 인코딩되면 한 번 디코딩해도 %ED%99%94... 형태로 남는다
                tmapServer.expect(request -> {
                            String decoded = URLDecoder.decode(
                                    request.getURI().toString(), StandardCharsets.UTF_8);
                            assertThat(decoded.contains("searchKeyword=강남 스타벅스")).isTrue();
                        })
                        .andRespond(withSuccess(POI_SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

                //when
                locationService.searchLocation("강남 스타벅스");

                //then
                tmapServer.verify();
            }
        }

        @Nested
        @DisplayName("Context: 검색 결과가 없어 티맵이 204를 응답하면")
        class Context_with_no_content {

            @Test
            @DisplayName("It : 예외 없이 빈 결과를 반환한다")
            void it_returns_empty_result() {
                //given : 티맵은 0건일 때 200이 아니라 204 No Content를 준다
                tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/pois")))
                        .andRespond(withStatus(HttpStatus.NO_CONTENT));

                //when
                LocationSearchResponse response = locationService.searchLocation("asdfqwerzxcv");

                //then
                assertThat(response.totalCount()).isEqualTo(0);
                assertThat(response.count()).isEqualTo(0);
                assertThat(response.page()).isEqualTo(0);
                assertThat(response.infos().isEmpty()).isTrue();
                tmapServer.verify();
            }
        }

        @Nested
        @DisplayName("Context: 티맵이 서버 오류를 응답하면")
        class Context_with_tmap_server_error {

            @Test
            @DisplayName("It : TMAP_API_ERROR 오류 발생")
            void it_throws_tmap_api_error() {
                //given
                tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/pois")))
                        .andRespond(withServerError());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> locationService.searchLocation("화목순대국"));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TMAP_API_ERROR);
            }
        }
    }
}
