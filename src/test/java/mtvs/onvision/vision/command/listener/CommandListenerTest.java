package mtvs.onvision.vision.command.listener;

import mtvs.onvision.vision.alert.service.FcmService;
import mtvs.onvision.vision.command.domain.CommandType;
import mtvs.onvision.vision.command.event.GuardianInstructed;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommandListener의")
class CommandListenerTest {

    @InjectMocks
    private CommandListener commandListener;

    @Mock
    private FcmService fcmService;

    @Mock
    private UserService userService;

    Long commandId = 10L;
    Long wardId = 2L;

    Instant occurredAt = Instant.parse("2026-08-10T05:31:00Z");

    GuardianInstructed event = new GuardianInstructed(commandId, "잠시 멈추세요.", occurredAt, wardId);

    @Nested
    @DisplayName("Describe: handleGuardianInstructedEvent 메서드는")
    class Describe_with_handleGuardianInstructedEvent {

        @Nested
        @DisplayName("Context: 피보호자에게 등록된 기기가 있으면")
        class Context_with_registered_device {

            @Test
            @DisplayName("It : 보호자가 아니라 피보호자의 기기를 찾는다")
            void it_looks_up_ward_devices() {
                //given
                given(userService.getFids(wardId)).willReturn(List.of("fid-1"));

                //when
                commandListener.handleGuardianInstructedEvent(event);

                //then
                verify(userService).getFids(wardId);
            }

            @Test
            @DisplayName("It : 이벤트의 값을 그대로 실어 보낸다")
            void it_passes_event_values() {
                //given : occurredAt을 여기서 다시 만들면 앱의 폐기 판단 기준이 어긋난다
                given(userService.getFids(wardId)).willReturn(List.of("fid-1"));

                //when
                commandListener.handleGuardianInstructedEvent(event);

                //then
                verify(fcmService).sendToDevice(
                        eq(commandId),
                        eq("잠시 멈추세요."),
                        eq(CommandType.GUARDIAN_INSTRUCTION),
                        eq(occurredAt),
                        eq("fid-1"));
            }

            @Test
            @DisplayName("(기기가 여러 대면)It : 기기마다 한 번씩 보낸다")
            void it_sends_to_every_device() {
                //given
                given(userService.getFids(wardId)).willReturn(List.of("fid-1", "fid-2"));

                //when
                commandListener.handleGuardianInstructedEvent(event);

                //then
                verify(fcmService).sendToDevice(anyLong(), anyString(), any(CommandType.class), any(Instant.class), eq("fid-1"));
                verify(fcmService).sendToDevice(anyLong(), anyString(), any(CommandType.class), any(Instant.class), eq("fid-2"));
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
                commandListener.handleGuardianInstructedEvent(event);

                //then
                verifyNoInteractions(fcmService);
            }

            @Test
            @DisplayName("(예외로 만들지 않는다)It : 조용히 끝낸다")
            void it_returns_quietly() {
                //given : 앱이 꺼져 있으면 버리기로 했으므로 실패가 아니다
                given(userService.getFids(wardId)).willReturn(List.of());

                //when
                commandListener.handleGuardianInstructedEvent(event);

                //then
                verify(fcmService, never()).sendToDevice(anyLong(), anyString(), any(CommandType.class), any(Instant.class), anyString());
            }
        }
    }
}
