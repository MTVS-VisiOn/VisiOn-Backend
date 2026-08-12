package mtvs.onvision.vision.alert.scheduler;

import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.alert.dto.RetryTarget;
import mtvs.onvision.vision.alert.service.AlertDeliveryService;
import mtvs.onvision.vision.common.service.FcmService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertRetryScheduler의")
class AlertRetrySchedulerTest {

    @InjectMocks
    private AlertRetryScheduler alertRetryScheduler;

    @Mock
    private AlertDeliveryService alertDeliveryService;

    @Mock
    private FcmService fcmService;

    Instant occurredAt = Instant.parse("2026-08-06T06:12:00Z");

    RetryTarget phone = new RetryTarget(100L, 10L, AlertType.OBSTACLE, occurredAt, "fid-phone");
    RetryTarget tablet = new RetryTarget(101L, 10L, AlertType.OBSTACLE, occurredAt, "fid-tablet");

    @Nested
    @DisplayName("Describe: retryFailedDeliveries 메서드는")
    class Describe_with_retryFailedDeliveries {

        @Nested
        @DisplayName("Context: 재전송 대상이 없으면")
        class Context_without_targets {

            @Test
            @DisplayName("It : 만료 처리만 하고 전송하지 않는다")
            void it_does_not_send() {
                //given
                given(alertDeliveryService.findRetryTargets()).willReturn(List.of());

                //when
                alertRetryScheduler.retryFailedDeliveries();

                //then
                verify(alertDeliveryService).expireOldDeliveries();
                verifyNoInteractions(fcmService);
            }
        }

        @Nested
        @DisplayName("Context: 재전송 대상이 있으면")
        class Context_with_targets {

            @Test
            @DisplayName("It : 대상마다 전송하고 결과를 반영한다")
            void it_sends_and_applies_each() {
                //given
                given(alertDeliveryService.findRetryTargets()).willReturn(List.of(phone, tablet));
                given(fcmService.sendToDevice(10L, AlertType.OBSTACLE, occurredAt, "fid-phone"))
                        .willReturn(NotifyStatus.SENT);
                given(fcmService.sendToDevice(10L, AlertType.OBSTACLE, occurredAt, "fid-tablet"))
                        .willReturn(NotifyStatus.FAILED);

                //when
                alertRetryScheduler.retryFailedDeliveries();

                //then
                verify(alertDeliveryService).applyResult(100L, NotifyStatus.SENT);
                verify(alertDeliveryService).applyResult(101L, NotifyStatus.FAILED);
            }

            @Test
            @DisplayName("It : 만료 처리를 재전송 조회보다 먼저 한다")
            void it_expires_before_querying() {
                //given - 순서가 뒤집히면 이미 만료된 건까지 한 번 더 발송된다
                given(alertDeliveryService.findRetryTargets()).willReturn(List.of());

                //when
                alertRetryScheduler.retryFailedDeliveries();

                //then
                InOrder order = inOrder(alertDeliveryService);
                order.verify(alertDeliveryService).expireOldDeliveries();
                order.verify(alertDeliveryService).findRetryTargets();
            }
        }

        @Nested
        @DisplayName("Context: 한 대상이 UNREGISTERED로 끝나도")
        class Context_with_unregistered {

            @Test
            @DisplayName("It : 나머지 대상 전송을 계속한다")
            void it_continues() {
                //given
                given(alertDeliveryService.findRetryTargets()).willReturn(List.of(phone, tablet));
                given(fcmService.sendToDevice(10L, AlertType.OBSTACLE, occurredAt, "fid-phone"))
                        .willReturn(NotifyStatus.UNREGISTERED);
                given(fcmService.sendToDevice(10L, AlertType.OBSTACLE, occurredAt, "fid-tablet"))
                        .willReturn(NotifyStatus.SENT);

                //when
                alertRetryScheduler.retryFailedDeliveries();

                //then
                verify(alertDeliveryService).applyResult(100L, NotifyStatus.UNREGISTERED);
                verify(alertDeliveryService).applyResult(101L, NotifyStatus.SENT);
            }
        }
    }
}
