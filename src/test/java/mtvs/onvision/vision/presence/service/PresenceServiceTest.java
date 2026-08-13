package mtvs.onvision.vision.presence.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.presence.domain.GuardianStreamStatus;
import mtvs.onvision.vision.presence.domain.NetworkType;
import mtvs.onvision.vision.presence.domain.PresenceType;
import mtvs.onvision.vision.presence.dto.HeartbeatRequest;
import mtvs.onvision.vision.presence.dto.PresenceResponse;
import mtvs.onvision.vision.presence.event.LowBatteryDetected;
import mtvs.onvision.vision.presence.repository.PresenceRepository;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresenceService의")
class PresenceServiceTest {

    @InjectMocks
    private PresenceService presenceService;

    @Mock
    private PresenceRepository presenceRepository;

    @Mock
    private UserService userService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    Long guardianId = 1L;
    Long wardId = 2L;
    String heartbeatJson = "{\"battery\":80}";

    /** 배터리 판정에 쓰이는 시각. 이벤트의 occurredAt으로 그대로 실린다 */
    Instant lastHeartbeat = Instant.parse("2026-08-06T06:12:00Z");

    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);
    CurrentUser ward = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);

    /** \@Value 필드는 단위 테스트에서 주입되지 않는다. yml과 같은 값을 넣는다 */
    @BeforeEach
    void injectThresholds() {
        ReflectionTestUtils.setField(presenceService, "thresholds", List.of(20, 10, 5));
        ReflectionTestUtils.setField(presenceService, "thresholdSeconds", 120L);
    }

    /** lastSync가 2분 이내면 최근(isRecent=true)으로 판정된다 */
    private HeartbeatRequest heartbeat(boolean deviceConnected, Integer battery,
                                       boolean networkConnected, Instant lastSync) {
        return heartbeat(deviceConnected, battery, networkConnected, lastSync, GuardianStreamStatus.IDLE);
    }

    /** 보호자 영상 상태까지 지정한다. 그 외 검증에는 IDLE로 고정된 위 헬퍼를 쓴다 */
    private HeartbeatRequest heartbeat(boolean deviceConnected, Integer battery,
                                       boolean networkConnected, Instant lastSync,
                                       GuardianStreamStatus guardianStreamStatus) {
        return new HeartbeatRequest(
                deviceConnected,
                battery,
                new HeartbeatRequest.NetworkRequest(NetworkType.LTE, networkConnected),
                guardianStreamStatus,
                Instant.now(),
                lastSync
        );
    }

    /** 배터리 판정에는 battery와 lastHeartbeat만 쓰이므로 나머지는 고정한다 */
    private HeartbeatRequest batteryHeartbeat(int battery) {
        return new HeartbeatRequest(
                true,
                battery,
                new HeartbeatRequest.NetworkRequest(NetworkType.LTE, true),
                GuardianStreamStatus.IDLE,
                lastHeartbeat,
                Instant.now()
        );
    }

    /** Redis에 남아 있는 직전 heartbeat. 덮어쓰기 전에 읽힌다 */
    private void givenPreviousBattery(int battery) {
        given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
        given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class))
                .willReturn(batteryHeartbeat(battery));
    }

    @Nested
    @DisplayName("Describe: saveHeartBeat 메서드는")
    class Describe_with_saveHeartBeat {

        @Nested
        @DisplayName("Context: 올바른 heartbeat가 주어지면")
        class Context_with_available_heartbeat {

            @Test
            @DisplayName("It : 직렬화해서 요청자의 아이디로 저장한다")
            void it_success_save_heartbeat() {
                //given
                HeartbeatRequest request = heartbeat(true, 80, true, Instant.now());
                given(objectMapper.writeValueAsString(request)).willReturn(heartbeatJson);

                //when
                presenceService.saveHeartBeat(request, ward);

                //then
                verify(presenceRepository).saveHeartbeat(wardId, heartbeatJson);
            }
        }

        @Nested
        @DisplayName("Context: 네트워크가 연결된 heartbeat면")
        class Context_with_connected_network {

            @Test
            @DisplayName("It : lastSync를 점수로 감시 목록을 갱신한다")
            void it_marks_connected() {
                //given - 갱신된 시각이 곧 '마지막으로 정상 연결이었던 시각'이 된다
                Instant lastSync = Instant.parse("2026-08-06T06:12:00Z");

                //when
                presenceService.saveHeartBeat(heartbeat(true, 80, true, lastSync), ward);

                //then
                verify(presenceRepository).markConnected(wardId, lastSync);
            }
        }

        @Nested
        @DisplayName("Context: 네트워크가 끊긴 채 도착한 heartbeat면")
        class Context_with_disconnected_network {

            @Test
            @DisplayName("It : 감시 목록을 갱신하지 않는다")
            void it_does_not_mark_connected() {
                //given - 신호는 왔지만 getIsConnected 기준으로는 연결이 아니다.
                // 여기서 갱신해 버리면 늙지 않아 연결 끊김이 영영 감지되지 않는다
                Instant lastSync = Instant.parse("2026-08-06T06:12:00Z");

                //when
                presenceService.saveHeartBeat(heartbeat(true, 80, false, lastSync), ward);

                //then
                verify(presenceRepository, never()).markConnected(any(), any());
            }

            @Test
            @DisplayName("It : heartbeat 저장은 그대로 한다")
            void it_still_saves_heartbeat() {
                //given - 조회(GET /api/presence)는 이 값으로 상태를 판정하므로 저장은 막지 않는다
                given(objectMapper.writeValueAsString(any(HeartbeatRequest.class))).willReturn(heartbeatJson);

                //when
                presenceService.saveHeartBeat(heartbeat(true, 80, false, Instant.now()), ward);

                //then
                verify(presenceRepository).saveHeartbeat(wardId, heartbeatJson);
            }
        }

        @Nested
        @DisplayName("Context: 배터리가 임계값을 새로 내려가면")
        class Context_with_battery_crossing_threshold {

            @Test
            @DisplayName("It : 현재 배터리와 lastHeartbeat를 담아 LowBatteryDetected를 발행한다")
            void it_publishes_event() {
                //given - 22에서 18로 떨어져 임계값 20을 지났다. 정확히 20을 밟지 않아도 잡힌다
                givenPreviousBattery(22);

                //when
                presenceService.saveHeartBeat(batteryHeartbeat(18), ward);

                //then
                ArgumentCaptor<LowBatteryDetected> captor = ArgumentCaptor.forClass(LowBatteryDetected.class);
                verify(eventPublisher).publishEvent(captor.capture());

                assertThat(captor.getValue()).isEqualTo(new LowBatteryDetected(wardId, 18, lastHeartbeat));
            }

            @Test
            @DisplayName("It : 발행 여부와 무관하게 heartbeat는 저장한다")
            void it_still_saves_heartbeat() {
                //given
                givenPreviousBattery(22);

                //when
                presenceService.saveHeartBeat(batteryHeartbeat(18), ward);

                //then
                verify(presenceRepository).saveHeartbeat(eq(wardId), any());
            }
        }

        @Nested
        @DisplayName("Context: 한 번에 임계값 여러 개를 지나가면")
        class Context_with_battery_crossing_multiple_thresholds {

            @Test
            @DisplayName("It : 한 번만 발행하고 가장 급한 현재값을 싣는다")
            void it_publishes_once() {
                //given - 15에서 4로 떨어져 10과 5를 함께 지났다
                givenPreviousBattery(15);

                //when
                presenceService.saveHeartBeat(batteryHeartbeat(4), ward);

                //then - 임계값마다 보내면 푸시가 동시에 두 개 뜬다
                ArgumentCaptor<LowBatteryDetected> captor = ArgumentCaptor.forClass(LowBatteryDetected.class);
                verify(eventPublisher).publishEvent(captor.capture());

                assertThat(captor.getValue()).isEqualTo(new LowBatteryDetected(wardId, 4, lastHeartbeat));
            }
        }

        @Nested
        @DisplayName("Context: 배터리가 줄었지만 임계값을 넘지 않으면")
        class Context_with_battery_above_threshold {

            @Test
            @DisplayName("It : 발행하지 않는다")
            void it_does_not_publish() {
                //given
                givenPreviousBattery(18);

                //when
                presenceService.saveHeartBeat(batteryHeartbeat(17), ward);

                //then
                verifyNoInteractions(eventPublisher);
            }
        }

        @Nested
        @DisplayName("Context: 배터리가 임계값에 머무르면")
        class Context_with_battery_staying_on_threshold {

            @Test
            @DisplayName("It : 발행하지 않는다 (heartbeat마다 반복 발송되지 않는다)")
            void it_does_not_publish() {
                //given - 이미 20에서 알림이 나갔고 여전히 20이다
                givenPreviousBattery(20);

                //when
                presenceService.saveHeartBeat(batteryHeartbeat(20), ward);

                //then
                verifyNoInteractions(eventPublisher);
            }
        }

        @Nested
        @DisplayName("Context: 충전으로 배터리가 올라가면")
        class Context_with_battery_charging {

            @Test
            @DisplayName("It : 발행하지 않는다")
            void it_does_not_publish() {
                //given - 15에서 20이 됐다. 하강이 아니므로 사건이 아니다
                givenPreviousBattery(15);

                //when
                presenceService.saveHeartBeat(batteryHeartbeat(20), ward);

                //then
                verifyNoInteractions(eventPublisher);
            }
        }

        @Nested
        @DisplayName("Context: 직전 heartbeat가 없으면")
        class Context_without_previous_heartbeat {

            @Test
            @DisplayName("It : 배터리가 낮아도 발행하지 않는다")
            void it_does_not_publish() {
                //given - 첫 heartbeat이거나 presence TTL(180초)이 지나 키가 사라진 상태다.
                // 비교 대상이 없으면 '내려갔다'를 판정할 수 없다
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.empty());

                //when
                presenceService.saveHeartBeat(batteryHeartbeat(8), ward);

                //then
                verifyNoInteractions(eventPublisher);
            }
        }
    }

    @Nested
    @DisplayName("Describe: getWardPresence 메서드는")
    class Describe_with_getWardPresence {

        @Nested
        @DisplayName("Context: 피보호자의 heartbeat가 존재하지 않으면")
        class Context_with_no_heartbeat {

            @Test
            @DisplayName("It : 배터리 null, 미연결, NOT_FOUND 상태를 반환한다")
            void it_return_not_found() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.empty());

                //when
                PresenceResponse response = presenceService.getWardPresence(guardian);

                //then
                assertThat(response.battery()).isNull();
                assertThat(response.deviceConnected()).isFalse();
                assertThat(response.deviceNetwork()).isFalse();
                assertThat(response.status()).isEqualTo(PresenceType.NOT_FOUND.getDescription());
                assertThat(response.guardianStreamStatus()).isNull();
            }
        }

        @Nested
        @DisplayName("Context: 동기화가 최근이고 네트워크가 연결되어 있으면")
        class Context_with_recent_sync_and_network {

            @Test
            @DisplayName("It : NORMAL 상태와 배터리를 반환한다")
            void it_return_normal() {
                //given
                HeartbeatRequest heartbeat = heartbeat(true, 77, true, Instant.now().minusSeconds(10));
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when
                PresenceResponse response = presenceService.getWardPresence(guardian);

                //then
                assertThat(response.battery()).isEqualTo(77);
                assertThat(response.deviceConnected()).isTrue();
                assertThat(response.deviceNetwork()).isTrue();
                assertThat(response.status()).isEqualTo(PresenceType.NORMAL.getDescription());
            }
        }

        @Nested
        @DisplayName("Context: 동기화는 최근이지만 네트워크가 끊겨 있으면")
        class Context_with_recent_sync_and_no_network {

            @Test
            @DisplayName("It : NOT_NETWORK 상태를 반환한다")
            void it_return_not_network() {
                //given
                HeartbeatRequest heartbeat = heartbeat(true, 50, false, Instant.now().minusSeconds(10));
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when
                PresenceResponse response = presenceService.getWardPresence(guardian);

                //then : deviceConnected(Quest↔폰)는 살아 있고 deviceNetwork(Quest↔인터넷)만 끊긴 상태
                assertThat(response.deviceConnected()).isTrue();
                assertThat(response.deviceNetwork()).isFalse();
                assertThat(response.status()).isEqualTo(PresenceType.NOT_NETWORK.getDescription());
            }
        }

        @Nested
        @DisplayName("Context: 동기화가 2분보다 오래되었고 네트워크는 연결되어 있으면")
        class Context_with_stale_sync_and_network {

            @Test
            @DisplayName("It : DELAY_SYNC 상태를 반환한다")
            void it_return_delay_sync() {
                //given
                HeartbeatRequest heartbeat = heartbeat(true, 50, true, Instant.now().minusSeconds(200));
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when
                PresenceResponse response = presenceService.getWardPresence(guardian);

                //then
                assertThat(response.status()).isEqualTo(PresenceType.DELAY_SYNC.getDescription());
            }
        }

        @Nested
        @DisplayName("Context: 동기화가 오래되었고 네트워크도 끊겨 있으면")
        class Context_with_stale_sync_and_no_network {

            @Test
            @DisplayName("It : NOT_FOUND 상태를 반환한다")
            void it_return_not_found() {
                //given
                HeartbeatRequest heartbeat = heartbeat(false, 50, false, Instant.now().minusSeconds(200));
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when
                PresenceResponse response = presenceService.getWardPresence(guardian);

                //then
                assertThat(response.status()).isEqualTo(PresenceType.NOT_FOUND.getDescription());
            }
        }

        @Nested
        @DisplayName("Context: heartbeat에 보호자 영상 상태가 실려 있으면")
        class Context_with_guardian_stream_status {

            @Test
            @DisplayName("It : 저장된 값을 그대로 반환한다")
            void it_return_guardian_stream_status() {
                //given
                HeartbeatRequest heartbeat =
                        heartbeat(true, 77, true, Instant.now().minusSeconds(10), GuardianStreamStatus.STREAMING);
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when
                PresenceResponse response = presenceService.getWardPresence(guardian);

                //then
                assertThat(response.guardianStreamStatus()).isEqualTo(GuardianStreamStatus.STREAMING);
            }
        }

        @Nested
        @DisplayName("Context: 동기화가 오래됐는데 영상 상태는 streaming으로 남아 있으면")
        class Context_with_stale_guardian_stream_status {

            @Test
            @DisplayName("It : 현재 구현은 streaming을 그대로 반환한다 (신선도 판정을 타지 않는다)")
            void it_return_stale_status_as_is() {
                //given - 마지막 heartbeat가 200초 전이라 status는 DELAY_SYNC로 내려간다.
                // 그런데 guardianStreamStatus는 같은 판정을 타지 않아 값이 그대로 남는다.
                // 보호자 화면에 '영상 나오는 중'이 최대 presence TTL(180초)만큼 남을 수 있다.
                // 오래된 값을 UNKNOWN으로 내릴지는 미결이며, 정하면 이 테스트를 함께 고친다
                HeartbeatRequest heartbeat =
                        heartbeat(true, 50, true, Instant.now().minusSeconds(200), GuardianStreamStatus.STREAMING);
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when
                PresenceResponse response = presenceService.getWardPresence(guardian);

                //then
                assertThat(response.status()).isEqualTo(PresenceType.DELAY_SYNC.getDescription());
                assertThat(response.guardianStreamStatus()).isEqualTo(GuardianStreamStatus.STREAMING);
            }
        }
    }

    @Nested
    @DisplayName("Describe: getIsConnected 메서드는")
    class Describe_with_getIsConnected {

        @Nested
        @DisplayName("Context: heartbeat가 존재하지 않으면")
        class Context_with_no_heartbeat {

            @Test
            @DisplayName("It : false를 반환한다 (Optional.get으로 터지지 않는다)")
            void it_return_false() {
                //given
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.empty());

                //when&then
                assertThat(presenceService.getIsConnected(wardId)).isFalse();
            }
        }

        @Nested
        @DisplayName("Context: 동기화가 최근이고 네트워크가 연결되어 있으면")
        class Context_with_recent_sync_and_network {

            @Test
            @DisplayName("It : true를 반환한다")
            void it_return_true() {
                //given
                HeartbeatRequest heartbeat = heartbeat(true, 80, true, Instant.now().minusSeconds(30));
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when&then
                assertThat(presenceService.getIsConnected(wardId)).isTrue();
            }
        }

        @Nested
        @DisplayName("Context: 동기화가 2분보다 오래되었으면")
        class Context_with_stale_sync {

            @Test
            @DisplayName("It : false를 반환한다")
            void it_return_false() {
                //given
                HeartbeatRequest heartbeat = heartbeat(true, 80, true, Instant.now().minusSeconds(200));
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when&then
                assertThat(presenceService.getIsConnected(wardId)).isFalse();
            }
        }

        @Nested
        @DisplayName("Context: 네트워크가 끊겨 있으면")
        class Context_with_no_network {

            @Test
            @DisplayName("It : false를 반환한다")
            void it_return_false() {
                //given
                HeartbeatRequest heartbeat = heartbeat(true, 80, false, Instant.now().minusSeconds(10));
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when&then
                assertThat(presenceService.getIsConnected(wardId)).isFalse();
            }
        }
    }
}
