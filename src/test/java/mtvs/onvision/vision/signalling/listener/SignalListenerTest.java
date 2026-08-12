package mtvs.onvision.vision.signalling.listener;

import mtvs.onvision.vision.common.constant.DataMessageType;
import mtvs.onvision.vision.common.service.FcmService;
import mtvs.onvision.vision.signalling.event.GuardianEntered;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignalListener의")
class SignalListenerTest {

    @InjectMocks
    private SignalListener signalListener;

    @Mock
    private FcmService fcmService;

    @Mock
    private UserService userService;

    Long wardId = 2L;

    Instant occurredAt = Instant.parse("2026-08-12T05:31:00Z");

    GuardianEntered event = new GuardianEntered(wardId, occurredAt);

    @Nested
    @DisplayName("Describe: handleGuardianEnteredEvent 메서드는")
    class Describe_with_handleGuardianEnteredEvent {

        @Nested
        @DisplayName("Context: 피보호자에게 등록된 기기가 있으면")
        class Context_with_registered_device {

            @Test
            @DisplayName("It : 방을 연 보호자가 아니라 피보호자의 기기를 찾는다")
            void it_looks_up_ward_devices() {
                //given
                given(userService.getFids(wardId)).willReturn(List.of("fid-1"));

                //when
                signalListener.handleGuardianEnteredEvent(event);

                //then
                verify(userService).getFids(wardId);
            }

            @Test
            @DisplayName("It : GUARDIAN_ENTERED 로 이벤트의 발생 시각을 그대로 실어 보낸다")
            void it_passes_event_values() {
                //given : occurredAt을 여기서 다시 만들면 앱의 폐기 판단 기준이 어긋난다
                given(userService.getFids(wardId)).willReturn(List.of("fid-1"));

                //when
                signalListener.handleGuardianEnteredEvent(event);

                //then
                verify(fcmService).sendSignalReady(
                        eq(DataMessageType.GUARDIAN_ENTERED.name()),
                        eq(occurredAt),
                        eq("fid-1"));
            }

            @Test
            @DisplayName("(저장되는 이벤트가 아니다)It : 지시 경로로는 보내지 않는다")
            void it_does_not_use_command_path() {
                //given : sendToDevice 는 alertId 를 싣고 TTL 이 24h 다. 늦게 배달되면
                //        피보호자가 이미 사라진 방에 join_room 을 보낸다
                given(userService.getFids(wardId)).willReturn(List.of("fid-1"));

                //when
                signalListener.handleGuardianEnteredEvent(event);

                //then
                verify(fcmService, never()).sendToDevice(any(), anyString(), any(DataMessageType.class), any(Instant.class), anyString());
            }

            @Test
            @DisplayName("(기기가 여러 대면)It : 기기마다 한 번씩 보낸다")
            void it_sends_to_every_device() {
                //given
                given(userService.getFids(wardId)).willReturn(List.of("fid-1", "fid-2"));

                //when
                signalListener.handleGuardianEnteredEvent(event);

                //then
                verify(fcmService).sendSignalReady(anyString(), any(Instant.class), eq("fid-1"));
                verify(fcmService).sendSignalReady(anyString(), any(Instant.class), eq("fid-2"));
            }
        }

        @Nested
        @DisplayName("Context: 피보호자에게 등록된 기기가 없으면")
        class Context_without_device {

            @Test
            @DisplayName("It : FCM을 호출하지 않는다")
            void it_does_not_send() {
                //given
                given(userService.getFids(wardId)).willReturn(List.of());

                //when
                signalListener.handleGuardianEnteredEvent(event);

                //then
                verifyNoInteractions(fcmService);
            }

            @Test
            @DisplayName("(예외로 만들지 않는다)It : 조용히 끝낸다")
            void it_returns_quietly() {
                //given : 기기가 꺼져 있으면 버리기로 했으므로 실패가 아니다
                given(userService.getFids(wardId)).willReturn(List.of());

                //when
                signalListener.handleGuardianEnteredEvent(event);

                //then
                verify(fcmService, never()).sendSignalReady(anyString(), any(Instant.class), anyString());
            }
        }
    }
}
