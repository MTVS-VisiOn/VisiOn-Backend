package mtvs.onvision.vision.location.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.location.domain.MovementStatus;
import mtvs.onvision.vision.location.dto.*;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
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
    private RealtimeLocationRepository realtimeLocationRepository;

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
                realtimeLocationRepository,
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

    private LocationReport report(Double lat, Double lon, Float accuracy, MovementStatus status,
                                  Instant recordedAt, MovementAnchor anchor) {
        return new LocationReport(wardId, lat, lon, accuracy, status, recordedAt, anchor);
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
        @DisplayName("Context: 차량 속도가 나왔지만 아직 연속 판정이 모자라면")
        class Context_with_unconfirmed_vehicle {

            /**
             * 이 케이스가 생긴 이유.
             * <p>
             * 2026-08-25 실기기 검증에서 걷는 중에 IN_VEHICLE이 세 번 나왔다. 전부 GPS가 한두
             * 보고 동안 20~40m 튄 것이었고(그 순간 accuracy도 두 배가 됐다), 시속 17~37km가
             * 찍혔다. 한 번의 속도 계산으로 확정하면 보호자 화면에 "차량 이동중"이 뜬다.
             */
            @Test
            @DisplayName("It : 확정하지 않고 ON_FOOT으로 내보내되 연속 횟수는 올린다")
            void it_does_not_confirm_on_first_hit() {
                //given : 위도 0.01도 ≈ 1111m, 30초 → 약 37 m/s
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(30), 0, null);
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.ON_FOOT, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.51, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(1);
            }

            @Test
            @DisplayName("It : 연속 2회까지도 확정하지 않는다")
            void it_does_not_confirm_on_second_hit() {
                //given : 오판 3건 중 가장 길게 이어진 것이 2회였다 (2026-08-25 14:14)
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(30), 1, null);
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.ON_FOOT, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.51, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(2);
            }
        }

        @Nested
        @DisplayName("Context: 차량 속도가 연속 3회 나오면")
        class Context_with_confirmed_vehicle {

            /** 실제 버스 탑승 2건은 연속 5회·3회였다 (2026-08-25 15:03, 15:06) */
            @Test
            @DisplayName("It : IN_VEHICLE로 확정한다")
            void it_confirms_in_vehicle() {
                //given
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(30), 2, null);
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.ON_FOOT, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.51, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.IN_VEHICLE);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(3);
            }
        }

        @Nested
        @DisplayName("Context: 차량 확정 뒤 믿을 만한 구간에서 보행 속도가 나오면")
        class Context_with_vehicle_exit {

            @Test
            @DisplayName("It : 연속 횟수를 0으로 되돌리고 ON_FOOT으로 내려온다")
            void it_resets_streak_and_drops_to_on_foot() {
                //given : 위도 0.0004도 ≈ 44m, 30초 → 약 1.48 m/s
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(30), 3, null);
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.IN_VEHICLE, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.5004, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(0);
            }

            @Test
            @DisplayName("It : 내려온 시각을 남긴다")
            void it_records_vehicle_exit_time() {
                //given : 이 시각이 있어야 다시 탔을 때 재진입인지 신규 탑승인지 구분된다
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(30), 3, null);
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.IN_VEHICLE, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.5004, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.anchor().vehicleExitAt()).isEqualTo(now);
            }
        }

        @Nested
        @DisplayName("Context: 내린 지 얼마 안 돼 다시 차량 속도가 나오면")
        class Context_with_vehicle_reentry {

            /**
             * 이 케이스가 생긴 이유.
             * <p>
             * 2026-08-25 퇴근시간대 재검증에서 실제로는 버스 안이었던 4분 51초가 ON_FOOT으로
             * 나갔다. 정체로 속도가 보행 수준까지 떨어지고 신호 정차가 잦아, 연속 3회를 채우기
             * 전에 계속 0으로 밀렸기 때문이다. 방금 전까지 차량이었다는 사실 자체가 근거다.
             */
            @Test
            @DisplayName("It : 연속 2회로 재확정한다")
            void it_confirms_with_two_hits_after_recent_exit() {
                //given : 30초 전에 내렸고 이번이 연속 2회째
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(30), 1, now.minusSeconds(30));
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.ON_FOOT, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.51, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.IN_VEHICLE);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(2);
            }

            /** 같은 날 오판 3건은 전부 하차 180초 안에 났지만 연속 1회로 끝났다 */
            @Test
            @DisplayName("It : 그래도 1회로는 확정하지 않는다")
            void it_still_rejects_a_single_hit() {
                //given : 하차 4초 뒤 GPS가 튀어 7.4 m/s가 찍힌 상황 (2026-08-25 18:36:33)
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(30), 0, now.minusSeconds(4));
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.ON_FOOT, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.51, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(1);
            }

            @Test
            @DisplayName("It : 내린 지 오래됐으면 원래대로 3회를 채워야 한다")
            void it_requires_three_hits_after_a_stale_exit() {
                //given : 400초 전 하차. 이쯤이면 다른 이동이다
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(30), 1, now.minusSeconds(400));
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.ON_FOOT, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.51, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(2);
            }
        }

        @Nested
        @DisplayName("Context: 앵커가 60초를 넘겨 묵어 속도를 신뢰할 수 없으면")
        class Context_with_stale_anchor {

            /**
             * 2026-08-25 15:06 실기기 재현.
             * <p>
             * 버스 안에서 GPS를 놓치자 단말이 100초 동안 같은 좌표만 되풀이해 보냈다. 그동안
             * 앵커가 묶여 있다가 다음 픽스에서 187m를 한 번에 받았고, 102초로 나누니 1.83 m/s가
             * 나와 달리는 버스가 도보로 떨어졌다. 평균 속도는 그 사이 무슨 일이 있었는지 말해주지
             * 못한다 — 마지막 몇 초에 몰아서 이동했을 수도 있다.
             */
            @Test
            @DisplayName("It : 이미 확정된 차량 판정을 뒤집지 않는다")
            void it_keeps_confirmed_vehicle() {
                //given : 위도 0.00168도 ≈ 187m, 102초 → 약 1.83 m/s (보행 속도로 계산된다)
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(102), 3, null);
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.IN_VEHICLE, now.minusSeconds(30), anchor);
                LocationRequest request = request(37.50168, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.IN_VEHICLE);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(3);
            }

            @Test
            @DisplayName("It : 쌓아둔 연속 횟수를 0으로 밀지 않는다")
            void it_does_not_reset_streak() {
                //given : 확정 직전(2회)에 보고가 끊겼다. 여기서 0으로 밀면 처음부터 다시 세야 한다
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 5f, now.minusSeconds(102), 2, null);
                LocationReport previous = report(37.5, 127.0, 5f, MovementStatus.ON_FOOT, now.minusSeconds(30), anchor);
                LocationRequest request = request(37.50168, 127.0, 5f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.anchor().vehicleStreak()).isEqualTo(2);
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

        @Nested
        @DisplayName("Context: 실제 운영 간격(3초)으로 보행 속도만큼 이동하면")
        class Context_with_walking_at_report_interval {

            /**
             * 이 테스트가 이 파일에 있는 이유.
             * <p>
             * 기존 케이스는 전부 30~60초 간격 픽스처였고, 실제 운영 간격인 3초를 태우는 케이스가
             * 하나도 없었다. 3초 동안 걸어서 움직이는 거리는 시속 5km라도 4.2m라서 오차 반경
             * (accuracy 3m대면 6m)을 못 넘는다. 그래서 테스트는 전부 녹색인데 실기기에서는
             * 어떤 속도로 걸어도 STATIONARY만 나왔다 (2026-08-24 검증).
             */
            @Test
            @DisplayName("It : 앵커를 들고 가 누적 변위로 ON_FOOT을 판별한다")
            void it_classify_on_foot_with_anchor() {
                //given : 앵커는 6초 전. 직전 보고(3초 전)는 아직 반경 안이라 판정이 보류된 상태였다
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                // 앵커에서 위도 0.0001도 ≈ 11.1m, 6초 → 약 1.85 m/s. 오차 반경 합은 6m
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 3f, now.minusSeconds(6), 0, null);
                LocationReport previous = report(37.50005, 127.0, 3f, MovementStatus.STATIONARY, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.5001, 127.0, 3f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                assertThat(captureSavedReport().status()).isEqualTo(MovementStatus.ON_FOOT);
            }

            @Test
            @DisplayName("It : 아직 오차 반경 안이면 직전 판정을 유지하고 앵커를 그대로 넘긴다")
            void it_keeps_previous_status_and_anchor() {
                //given : 3초 동안 4.4m. 오차 반경 6m 안이라 아직 판정할 근거가 없다
                Instant now = Instant.now();
                Instant anchorAt = now.minusSeconds(3);
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 3f, anchorAt, 0, null);
                LocationReport previous = report(37.5, 127.0, 3f, MovementStatus.ON_FOOT, anchorAt, anchor);
                LocationRequest request = request(37.50004, 127.0, 3f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then : 걷는 중에 3초마다 STATIONARY로 튀지 않는다. 앵커가 유지돼야 다음 보고에서 누적된다
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.anchor().recordedAt()).isEqualTo(anchorAt);
            }
        }

        @Nested
        @DisplayName("Context: 오차 반경 안에서 확정 시간이 지나면")
        class Context_with_anchor_held_long_enough {

            @Test
            @DisplayName("It : STATIONARY로 확정하고 앵커를 옮긴다")
            void it_confirm_stationary() {
                //given : 21초 동안 2.2m. 걷고 있었다면 진작 반경을 벗어났을 시간이다
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 3f, now.minusSeconds(21), 0, null);
                LocationReport previous = report(37.5, 127.0, 3f, MovementStatus.ON_FOOT, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.50002, 127.0, 3f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.STATIONARY);
                assertThat(saved.anchor().recordedAt()).isEqualTo(now);
            }
        }

        @Nested
        @DisplayName("Context: 오차 반경이 커서 아직 반경을 벗어날 시간이 안 됐으면")
        class Context_with_large_error_radius {

            /**
             * 정지 확정 시간을 20초로 못박으면, 오차 반경이 클 때 반경을 벗어나기도 전에
             * 정지가 먼저 확정된다. accuracy 15m(반경 30m)에서 시속 4.8km로 걸으면 30m를
             * 벗어나는 데 23초가 걸리는데, 20초에 STATIONARY로 잘라버린다.
             * 실기기 실외 정지 샘플이 13.826m였으므로 이 구간은 실제로 밟힌다.
             */
            @Test
            @DisplayName("It : 정지로 확정하지 않고 직전 판정을 유지한다")
            void it_waits_longer_when_radius_is_large() {
                //given : 21초 동안 28m. 반경 30m 안이지만 비례 확정 시간(30/0.3 = 100초)에는 한참 못 미친다
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
                MovementAnchor anchor = new MovementAnchor(37.5, 127.0, 15f, now.minusSeconds(21), 0, null);
                LocationReport previous = report(37.5002, 127.0, 15f, MovementStatus.ON_FOOT, now.minusSeconds(3), anchor);
                LocationRequest request = request(37.5002518, 127.0, 15f, now);

                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(previousJson));
                given(objectMapper.readValue(previousJson, LocationReport.class)).willReturn(previous);

                //when
                locationService.receiveLocation(request, ward);

                //then
                LocationReport saved = captureSavedReport();
                assertThat(saved.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(saved.anchor().recordedAt()).isEqualTo(now.minusSeconds(21));
            }
        }

        @Nested
        @DisplayName("Context: 앵커가 없는 옛 형식이 latest 키에 남아 있으면")
        class Context_with_legacy_report {

            @Test
            @DisplayName("It : 직전 보고를 기준점으로 삼아 판별한다")
            void it_falls_back_to_previous_point() {
                //given : 배포 직후에는 앵커 없는 값이 남아 있다. 위도 0.0004도 ≈ 44m, 30초 → 약 1.48 m/s
                Instant now = Instant.now();
                String previousJson = "{\"previous\":true}";
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
            @DisplayName("It : 좌표로 티맵을 호출해 좌표·도로명 주소·이동 상태·측정 시각을 반환한다")
            void it_return_address_and_status() {
                //given
                Instant recordedAt = Instant.parse("2026-08-04T05:32:10.123Z");
                String latestJson = "{\"latest\":true}";
                LocationReport latest = new LocationReport(
                        wardId, 37.5, 127.0, 10f, MovementStatus.ON_FOOT, recordedAt);

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
                assertThat(response.latitude()).isEqualTo(37.5);
                assertThat(response.longitude()).isEqualTo(127.0);
                assertThat(response.address()).isEqualTo("경기도 부천시 원미구 부일로 123");
                assertThat(response.status()).isEqualTo(MovementStatus.ON_FOOT.getMessage());
                assertThat(response.recordedAt()).isEqualTo(recordedAt);
                tmapServer.verify();
            }

            @Test
            @DisplayName("It : 측정 시각은 서버 수신 시각이 아니라 단말이 보낸 값 그대로다")
            void it_passes_through_recorded_at() {
                //given : 프론트가 §4.9의 "마지막 수신 n분 전"을 판단하는 근거라 가공하지 않는다
                Instant recordedAt = Instant.now().minusSeconds(600);
                String latestJson = "{\"latest\":true}";
                LocationReport latest = new LocationReport(
                        wardId, 37.5, 127.0, 10f, MovementStatus.STATIONARY, recordedAt);

                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(latestJson));
                given(objectMapper.readValue(latestJson, LocationReport.class)).willReturn(latest);

                tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/geo/reversegeocoding")))
                        .andRespond(withSuccess(TMAP_RESPONSE, MediaType.APPLICATION_JSON));

                //when
                LastLocationResponse response = locationService.getLastLocation(guardian);

                //then
                assertThat(response.recordedAt()).isEqualTo(recordedAt);
            }

            // "presence를 조회하지 않는다"(presence 게이트 제거, 2026-08-04) 테스트는 지웠다.
            // LocationService가 PresenceService를 아예 의존하지 않게 되어 컴파일러가 막아준다.
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
                              { "roadName": "새문안로5길", "fullAddressRoad": "서울 종로구 새문안로5길 11" }
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
                              { "roadName": "여의대방로", "fullAddressRoad": "서울 영등포구 여의대방로 383" }
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

        /**
         * 검색 중심 좌표가 있는 상태.
         * 최근 위치가 없으면 티맵을 아예 호출하지 않으므로, 검색 자체를 검증하는 테스트에는 전부 필요하다.
         */
        private void givenLastLocation() {
            String latestJson = "{\"latest\":true}";
            given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(latestJson));
            given(objectMapper.readValue(latestJson, LocationReport.class)).willReturn(
                    report(37.5665, 126.978, 10f, MovementStatus.STATIONARY, Instant.now()));
        }

        @Nested
        @DisplayName("Context: 검색 결과가 존재하면")
        class Context_with_results {

            @Test
            @DisplayName("It : 페이지 정보와 장소 목록을 반환한다")
            void it_returns_places() {
                //given
                givenLastLocation();
                expectPoiSearch();

                //when
                LocationSearchResponse response = locationService.searchLocation("화목순대국", ward);

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
                givenLastLocation();
                expectPoiSearch();

                //when
                LocationSearchInfo first = locationService.searchLocation("화목순대국", ward).infos().getFirst();

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
                givenLastLocation();
                expectPoiSearch();

                //when
                LocationSearchInfo first = locationService.searchLocation("화목순대국", ward).infos().getFirst();

                //then : detailAddrName이 빈 값이라 뒤에 공백도 남지 않는다
                assertThat(first.landAddress()).isEqualTo("서울 종로구 당주동 40");
            }

            @Test
            @DisplayName("(부번이 있음)It : 지번 주소를 본번-부번으로 조합한다")
            void it_joins_first_and_second_no() {
                //given
                givenLastLocation();
                expectPoiSearch();

                //when
                LocationSearchInfo second = locationService.searchLocation("화목순대국", ward).infos().get(1);

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
                givenLastLocation();
                tmapServer.expect(request -> {
                            String decoded = URLDecoder.decode(
                                    request.getURI().toString(), StandardCharsets.UTF_8);
                            assertThat(decoded.contains("searchKeyword=강남 스타벅스")).isTrue();
                        })
                        .andRespond(withSuccess(POI_SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

                //when
                locationService.searchLocation("강남 스타벅스", ward);

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
                givenLastLocation();
                tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/pois")))
                        .andRespond(withStatus(HttpStatus.NO_CONTENT));

                //when
                LocationSearchResponse response = locationService.searchLocation("asdfqwerzxcv", ward);

                //then
                assertThat(response.totalCount()).isEqualTo(0);
                assertThat(response.count()).isEqualTo(0);
                assertThat(response.page()).isEqualTo(0);
                assertThat(response.infos().isEmpty()).isTrue();
                tmapServer.verify();
            }
        }

        @Nested
        @DisplayName("Context: 최근 위치가 있으면")
        class Context_with_last_location {

            @Test
            @DisplayName("It : 그 좌표를 중심으로 거리순 정렬(radius=0)을 요청하고 center에 담아 돌려준다")
            void it_searches_around_last_location() {
                //given : searchtypCd=R 이면 radius 가 필수다. 빠지면 티맵이 9401(필수 파라메터 없음)로 400을 준다
                givenLastLocation();
                tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/pois")))
                        .andExpect(queryParam("centerLat", "37.5665"))
                        .andExpect(queryParam("centerLon", "126.978"))
                        .andExpect(queryParam("searchtypCd", "R"))
                        .andExpect(queryParam("radius", "0"))
                        .andRespond(withSuccess(POI_SEARCH_RESPONSE, MediaType.APPLICATION_JSON));

                //when
                LocationSearchResponse response = locationService.searchLocation("화목순대국", ward);

                //then
                assertThat(response.center()).isEqualTo(new CoordinateInfo(37.5665, 126.978));
                tmapServer.verify();
            }
        }

        @Nested
        @DisplayName("Context: 최근 위치가 없으면")
        class Context_with_no_last_location {

            @Test
            @DisplayName("It : 티맵을 호출하지 않고 center가 null인 빈 결과를 돌려준다")
            void it_returns_empty_without_calling_tmap() {
                //given : 30분 TTL이 지나면 위치는 그냥 사라진다. 오류가 아니라 빈 결과다
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.empty());

                //when : 기대한 요청을 걸지 않았으므로 티맵을 부르면 여기서 바로 깨진다
                LocationSearchResponse response = locationService.searchLocation("화목순대국", ward);

                //then
                assertThat(response.center()).isNull();
                assertThat(response.totalCount()).isEqualTo(0);
                assertThat(response.count()).isEqualTo(0);
                assertThat(response.page()).isEqualTo(0);
                assertThat(response.infos().isEmpty()).isTrue();
                tmapServer.verify();
            }
        }

        @Nested
        @DisplayName("Context: 최근 위치 JSON을 읽지 못하면")
        class Context_with_broken_last_location {

            @Test
            @DisplayName("It : 티맵 오류로 번지지 않고 center가 null인 빈 결과를 돌려준다")
            void it_does_not_leak_as_tmap_error() {
                //given : 역직렬화 실패를 TMAP_API_ERROR로 보고하면 원인 추적이 엉뚱한 곳으로 간다
                String brokenJson = "{broken";
                given(realtimeLocationRepository.getLastLocation(wardId)).willReturn(Optional.of(brokenJson));
                given(objectMapper.readValue(brokenJson, LocationReport.class))
                        .willThrow(new RuntimeException("deserialize failed"));

                //when
                LocationSearchResponse response = locationService.searchLocation("화목순대국", ward);

                //then
                assertThat(response.center()).isNull();
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
                givenLastLocation();
                tmapServer.expect(requestTo(startsWith(BASE_URL + "/tmap/pois")))
                        .andRespond(withServerError());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> locationService.searchLocation("화목순대국", ward));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TMAP_API_ERROR);
            }
        }
    }
}
