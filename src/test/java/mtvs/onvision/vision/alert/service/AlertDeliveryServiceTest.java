package mtvs.onvision.vision.alert.service;

import mtvs.onvision.vision.alert.domain.Alert;
import mtvs.onvision.vision.alert.domain.AlertDelivery;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.alert.dto.RetryTarget;
import mtvs.onvision.vision.alert.repository.AlertDeliveryRepository;
import mtvs.onvision.vision.alert.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertDeliveryService의")
class AlertDeliveryServiceTest {

    @InjectMocks
    private AlertDeliveryService alertDeliveryService;

    @Mock
    private AlertDeliveryRepository alertDeliveryRepository;

    @Mock
    private AlertRepository alertRepository;

    Long alertId = 10L;
    long expireMinutes = 5L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertDeliveryService, "expireMinutes", expireMinutes);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Collection<NotifyStatus>> statusCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<AlertDelivery>> deliveryCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    @Nested
    @DisplayName("Describe: createPending 메서드는")
    class Describe_with_createPending {

        @Nested
        @DisplayName("Context: 기기 목록이 비어 있으면")
        class Context_without_fids {

            @Test
            @DisplayName("It : 알림을 조회하지도, 저장하지도 않는다")
            void it_does_nothing() {
                //when
                alertDeliveryService.createPending(alertId, List.of());

                //then
                verifyNoInteractions(alertRepository, alertDeliveryRepository);
            }
        }

        @Nested
        @DisplayName("Context: 기기가 여러 대면")
        class Context_with_fids {

            @Test
            @DisplayName("It : 기기마다 PENDING 행을 만든다")
            void it_creates_one_row_per_device() {
                //given
                Alert alert = mock(Alert.class);
                given(alertRepository.getReferenceById(alertId)).willReturn(alert);

                //when
                alertDeliveryService.createPending(alertId, List.of("fid-phone", "fid-tablet"));

                //then
                ArgumentCaptor<List<AlertDelivery>> captor = deliveryCaptor();
                verify(alertDeliveryRepository).saveAll(captor.capture());

                assertThat(captor.getValue())
                        .hasSize(2)
                        .extracting(AlertDelivery::getFid)
                        .containsExactly("fid-phone", "fid-tablet");
                assertThat(captor.getValue())
                        .allMatch(delivery -> delivery.getStatus() == NotifyStatus.PENDING);
            }
        }
    }

    @Nested
    @DisplayName("Describe: applyResults 메서드는")
    class Describe_with_applyResults {

        @Nested
        @DisplayName("Context: 일부 기기만 결과에 있으면")
        class Context_with_partial_results {

            @Test
            @DisplayName("It : 결과에 있는 기기의 상태만 바꾼다")
            void it_updates_only_known_fids() {
                //given
                Alert alert = mock(Alert.class);
                AlertDelivery phone = new AlertDelivery(alert, "fid-phone");
                AlertDelivery tablet = new AlertDelivery(alert, "fid-tablet");
                given(alertDeliveryRepository.findAllByAlertId(alertId)).willReturn(List.of(phone, tablet));

                //when - tablet은 결과에 없다
                alertDeliveryService.applyResults(alertId, Map.of("fid-phone", NotifyStatus.SENT));

                //then
                assertThat(phone.getStatus()).isEqualTo(NotifyStatus.SENT);
                assertThat(phone.getAttemptCount()).isEqualTo(1);
                assertThat(tablet.getStatus()).isEqualTo(NotifyStatus.PENDING);
                assertThat(tablet.getAttemptCount()).isZero();
            }
        }
    }

    @Nested
    @DisplayName("Describe: applyResult 메서드는")
    class Describe_with_applyResult {

        @Nested
        @DisplayName("Context: 해당 발송 건이 있으면")
        class Context_with_existing_delivery {

            @Test
            @DisplayName("It : 상태를 바꾸고 시도 횟수를 올린다")
            void it_marks_result() {
                //given
                AlertDelivery delivery = new AlertDelivery(mock(Alert.class), "fid-phone");
                given(alertDeliveryRepository.findById(100L)).willReturn(Optional.of(delivery));

                //when
                alertDeliveryService.applyResult(100L, NotifyStatus.SENT);

                //then
                assertThat(delivery.getStatus()).isEqualTo(NotifyStatus.SENT);
                assertThat(delivery.getAttemptCount()).isEqualTo(1);
            }
        }

        @Nested
        @DisplayName("Context: 해당 발송 건이 없으면")
        class Context_without_delivery {

            @Test
            @DisplayName("It : 예외 없이 넘어간다")
            void it_does_nothing() {
                //given
                given(alertDeliveryRepository.findById(anyLong())).willReturn(Optional.empty());

                //when-then - 조용히 지나가므로 상태가 영영 안 바뀐다. 로그가 필요한 지점이다
                alertDeliveryService.applyResult(999L, NotifyStatus.SENT);
            }
        }
    }

    @Nested
    @DisplayName("Describe: expireOldDeliveries 메서드는")
    class Describe_with_expireOldDeliveries {

        @Nested
        @DisplayName("Context: 임계값을 넘긴 미발송 건이 있으면")
        class Context_with_old_deliveries {

            @Test
            @DisplayName("It : PENDING·FAILED만 골라 EXPIRED로 바꾼다")
            void it_expires_them() {
                //given
                Alert alert = mock(Alert.class);
                AlertDelivery pending = new AlertDelivery(alert, "fid-phone");
                AlertDelivery failed = new AlertDelivery(alert, "fid-tablet");
                failed.markResult(NotifyStatus.FAILED);
                given(alertDeliveryRepository.findAllByStatusInAndAlertOccurredAtLessThan(any(), any()))
                        .willReturn(List.of(pending, failed));

                //when
                alertDeliveryService.expireOldDeliveries();

                //then
                assertThat(pending.getStatus()).isEqualTo(NotifyStatus.EXPIRED);
                assertThat(failed.getStatus()).isEqualTo(NotifyStatus.EXPIRED);

                ArgumentCaptor<Collection<NotifyStatus>> captor = statusCaptor();
                verify(alertDeliveryRepository)
                        .findAllByStatusInAndAlertOccurredAtLessThan(captor.capture(), any());
                assertThat(captor.getValue())
                        .containsExactlyInAnyOrder(NotifyStatus.PENDING, NotifyStatus.FAILED);
            }
        }

        @Nested
        @DisplayName("Context: 대상이 없으면")
        class Context_without_old_deliveries {

            @Test
            @DisplayName("It : 아무것도 바꾸지 않는다")
            void it_does_nothing() {
                //given
                given(alertDeliveryRepository.findAllByStatusInAndAlertOccurredAtLessThan(any(), any()))
                        .willReturn(List.of());

                //when-then
                alertDeliveryService.expireOldDeliveries();
            }
        }
    }

    @Nested
    @DisplayName("Describe: findRetryTargets 메서드는")
    class Describe_with_findRetryTargets {

        @Nested
        @DisplayName("Context: 재전송 대상이 있으면")
        class Context_with_targets {

            @Test
            @DisplayName("It : 엔티티가 아니라 값으로 뽑아 돌려준다")
            void it_maps_to_value() {
                //given
                Instant occurredAt = Instant.parse("2026-08-06T06:12:00Z");
                Alert alert = mock(Alert.class);
                given(alert.getId()).willReturn(alertId);
                given(alert.getType()).willReturn(AlertType.OBSTACLE);
                given(alert.getOccurredAt()).willReturn(occurredAt);

                AlertDelivery delivery = mock(AlertDelivery.class);
                given(delivery.getId()).willReturn(100L);
                given(delivery.getAlert()).willReturn(alert);
                given(delivery.getFid()).willReturn("fid-phone");

                given(alertDeliveryRepository.findAllByStatusInAndAlertOccurredAtGreaterThanEqual(any(), any()))
                        .willReturn(List.of(delivery));

                //when
                List<RetryTarget> targets = alertDeliveryService.findRetryTargets();

                //then - 재전송도 원래 발생 시각으로 문구를 만들어야 한다
                assertThat(targets).containsExactly(
                        new RetryTarget(100L, alertId, AlertType.OBSTACLE, occurredAt, "fid-phone"));
            }

            @Test
            @DisplayName("It : 임계값을 현재보다 과거로 잡는다")
            void it_uses_past_threshold() {
                //given
                given(alertDeliveryRepository.findAllByStatusInAndAlertOccurredAtGreaterThanEqual(any(), any()))
                        .willReturn(List.of());
                Instant before = Instant.now().minusSeconds(expireMinutes * 60);

                //when
                alertDeliveryService.findRetryTargets();

                //then
                ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
                verify(alertDeliveryRepository)
                        .findAllByStatusInAndAlertOccurredAtGreaterThanEqual(any(), captor.capture());
                assertThat(captor.getValue()).isBetween(before.minusSeconds(5), Instant.now());
            }
        }

        @Nested
        @DisplayName("Context: 재전송 대상이 없으면")
        class Context_without_targets {

            @Test
            @DisplayName("It : 빈 목록을 돌려준다")
            void it_returns_empty() {
                //given
                given(alertDeliveryRepository.findAllByStatusInAndAlertOccurredAtGreaterThanEqual(any(), any()))
                        .willReturn(List.of());

                //when
                List<RetryTarget> targets = alertDeliveryService.findRetryTargets();

                //then
                assertThat(targets).isEmpty();
                verify(alertDeliveryRepository, never()).findAllByAlertId(anyLong());
            }
        }
    }
}
