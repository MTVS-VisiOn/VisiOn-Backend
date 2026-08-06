package mtvs.onvision.vision.alert.listener;

import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.alert.event.ObstacleDetected;
import mtvs.onvision.vision.alert.repository.AlertNotificationRepository;
import mtvs.onvision.vision.alert.service.AlertDeliveryService;
import mtvs.onvision.vision.alert.service.FcmService;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
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
    private AlertDeliveryService alertDeliveryService;

    @Mock
    private AlertNotificationRepository alertNotificationRepository;

    Long alertId = 10L;
    Long wardId = 2L;
    Long guardianId = 1L;

    Instant occurredAt = Instant.parse("2026-08-06T06:12:00Z");

    ObstacleDetected event = new ObstacleDetected(alertId, wardId, occurredAt);

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
                alertListener.handleObstacleEvent(event);

                //then
                ArgumentCaptor<List<String>> captor = fidCaptor();
                verify(fcmService).sendNotification(
                        eq(alertId), eq(AlertType.OBSTACLE), eq(occurredAt), captor.capture());

                assertThat(captor.getValue()).containsExactlyElementsOf(fids);
            }

            @Test
            @DisplayName("It : 발송 전에 PENDING을 남기고 발송 후 결과를 반영한다")
            void it_records_delivery_around_sending() {
                //given
                givenNotNotifiedYet();
                given(userService.getGuardianIdFromWardId(wardId)).willReturn(guardianId);
                given(userService.getFids(guardianId)).willReturn(fids);
                Map<String, NotifyStatus> results = Map.of(
                        "fid-phone", NotifyStatus.SENT,
                        "fid-tablet", NotifyStatus.FAILED);
                given(fcmService.sendNotification(alertId, AlertType.OBSTACLE, occurredAt, fids))
                        .willReturn(results);

                //when
                alertListener.handleObstacleEvent(event);

                //then - 순서가 뒤집히면 프로세스가 죽었을 때 스케줄러가 찾을 행이 없다
                InOrder order = inOrder(alertDeliveryService, fcmService);
                order.verify(alertDeliveryService).createPending(alertId, fids);
                order.verify(fcmService).sendNotification(alertId, AlertType.OBSTACLE, occurredAt, fids);
                order.verify(alertDeliveryService).applyResults(alertId, results);
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
                alertListener.handleObstacleEvent(event);

                //then
                ArgumentCaptor<List<String>> captor = fidCaptor();
                verify(fcmService).sendNotification(
                        eq(alertId), eq(AlertType.OBSTACLE), eq(occurredAt), captor.capture());

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
                assertThatThrownBy(() -> alertListener.handleObstacleEvent(event))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_RELATION);

                verifyNoInteractions(fcmService, alertDeliveryService);
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
                alertListener.handleObstacleEvent(event);

                //then
                verifyNoInteractions(userService, fcmService, alertDeliveryService);
            }
        }
    }
}
