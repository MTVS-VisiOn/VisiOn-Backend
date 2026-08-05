package mtvs.onvision.vision.alert.listener;

import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.event.ObstacleDetected;
import mtvs.onvision.vision.alert.repository.AlertNotificationRepository;
import mtvs.onvision.vision.alert.service.FcmService;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertListener의")
class AlertListenerTest {

    @InjectMocks
    private AlertListener alertListener;

    @Mock
    private FcmService fcmService;

    @Mock
    private UserService userService;

    @Mock
    private AlertNotificationRepository alertNotificationRepository;

    Long alertId = 10L;
    Long wardId = 2L;
    Long guardianId = 1L;

    ObstacleDetected event = new ObstacleDetected(alertId, wardId);

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<String>> fidCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    /** 아직 발송되지 않은 알림이라 발송권을 선점한 상황 */
    private void givenNotNotifiedYet() {
        given(alertNotificationRepository.markNotified(alertId)).willReturn(true);
    }

    @Nested
    @DisplayName("Describe: handleAlertEvent 메서드는")
    class Describe_with_handleAlertEvent {

        @Nested
        @DisplayName("Context: 보호자에게 등록된 기기가 있으면")
        class Context_with_registered_fids {

            List<String> fids = List.of("fid-phone", "fid-tablet");

            @Test
            @DisplayName("It : 피보호자로 보호자를 찾아 그 기기 목록으로 알림을 보낸다")
            void it_sends_notification_to_guardian_devices() {
                //given
                givenNotNotifiedYet();
                given(userService.getGuardianIdFromWardId(wardId)).willReturn(guardianId);
                given(userService.getFids(guardianId)).willReturn(fids);

                //when
                alertListener.handleAlertEvent(event);

                //then
                ArgumentCaptor<List<String>> captor = fidCaptor();
                verify(fcmService).sendNotification(
                        eq(alertId), eq(AlertType.OBSTACLE), anyString(), anyString(), captor.capture());

                assertThat(captor.getValue()).containsExactlyElementsOf(fids);
            }
        }

        @Nested
        @DisplayName("Context: 보호자에게 등록된 기기가 없으면")
        class Context_without_fids {

            @Test
            @DisplayName("It : 빈 목록을 그대로 넘긴다 (예외를 던지지 않는다)")
            void it_passes_empty_list() {
                //given
                givenNotNotifiedYet();
                given(userService.getGuardianIdFromWardId(wardId)).willReturn(guardianId);
                given(userService.getFids(guardianId)).willReturn(List.of());

                //when
                alertListener.handleAlertEvent(event);

                //then
                ArgumentCaptor<List<String>> captor = fidCaptor();
                verify(fcmService).sendNotification(
                        eq(alertId), eq(AlertType.OBSTACLE), anyString(), anyString(), captor.capture());

                assertThat(captor.getValue()).isEmpty();
            }
        }

        @Nested
        @DisplayName("Context: 피보호자에게 연결된 보호자가 없으면")
        class Context_without_relation {

            @Test
            @DisplayName("It : 알림을 보내지 않고 예외가 전파된다")
            void it_does_not_send() {
                //given
                givenNotNotifiedYet();
                given(userService.getGuardianIdFromWardId(wardId))
                        .willThrow(new BusinessException(ErrorCode.NOT_FOUND_RELATION));

                //when-then
                assertThatThrownBy(() -> alertListener.handleAlertEvent(event))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_RELATION);

                verifyNoInteractions(fcmService);
            }
        }

        @Nested
        @DisplayName("Context: 이미 발송된 알림이면")
        class Context_with_already_notified {

            @Test
            @DisplayName("It : 보호자를 조회하지도, 알림을 보내지도 않는다")
            void it_skips_duplicate() {
                //given
                given(alertNotificationRepository.markNotified(alertId)).willReturn(false);

                //when
                alertListener.handleAlertEvent(event);

                //then
                verifyNoInteractions(userService, fcmService);
            }
        }
    }
}
