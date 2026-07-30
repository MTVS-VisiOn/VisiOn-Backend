package mtvs.onvision.vision.presence.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.presence.domain.NetworkType;
import mtvs.onvision.vision.presence.domain.PresenceType;
import mtvs.onvision.vision.presence.dto.HeartbeatRequest;
import mtvs.onvision.vision.presence.dto.PresenceResponse;
import mtvs.onvision.vision.presence.repository.PresenceRepository;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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

    Long guardianId = 1L;
    Long wardId = 2L;
    String heartbeatJson = "{\"battery\":80}";

    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);
    CurrentUser ward = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);

    /** lastSync가 2분 이내면 최근(isRecent=true)으로 판정된다 */
    private HeartbeatRequest heartbeat(boolean deviceConnected, Integer battery,
                                       boolean networkConnected, Instant lastSync) {
        return new HeartbeatRequest(
                deviceConnected,
                battery,
                new HeartbeatRequest.NetworkRequest(NetworkType.LTE, networkConnected),
                Instant.now(),
                lastSync
        );
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
                assertThat(response.status()).isEqualTo(PresenceType.NOT_FOUND.getDescription());
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

                //then
                assertThat(response.status()).isEqualTo(PresenceType.NOT_NETWORK.getDescription());
            }
        }

        @Nested
        @DisplayName("Context: 동기화가 2분보다 오래되었고 네트워크는 연결되어 있으면")
        class Context_with_stale_sync_and_network {

            @Test
            @DisplayName("It : NOT_NETWORK 상태를 반환한다")
            void it_return_not_network() {
                //given
                HeartbeatRequest heartbeat = heartbeat(true, 50, true, Instant.now().minusSeconds(200));
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(presenceRepository.getLastHeartbeat(wardId)).willReturn(Optional.of(heartbeatJson));
                given(objectMapper.readValue(heartbeatJson, HeartbeatRequest.class)).willReturn(heartbeat);

                //when
                PresenceResponse response = presenceService.getWardPresence(guardian);

                //then
                assertThat(response.status()).isEqualTo(PresenceType.NOT_NETWORK.getDescription());
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
