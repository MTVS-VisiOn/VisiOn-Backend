package mtvs.onvision.vision.presence.scheduler;

import mtvs.onvision.vision.presence.event.DisconnectDetected;
import mtvs.onvision.vision.presence.repository.PresenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisconnectMonitor의")
class DisconnectMonitorTest {

    @InjectMocks
    private DisconnectMonitor disconnectMonitor;

    @Mock
    private PresenceRepository presenceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    long thresholdSeconds = 120;

    Long wardId = 2L;
    Long otherWardId = 3L;
    Instant lastSync = Instant.parse("2026-08-06T06:12:00Z");

    /**
     * {@code @Value} 필드는 단위 테스트에서 주입되지 않는다.
     * {@code startedAt}은 빈 생성 시각이라 기본값이면 유예 구간에 걸려 아무것도 하지 않는다.
     * 감지 자체를 보려면 기동한 지 충분히 지난 상태로 돌려야 한다.
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(disconnectMonitor, "thresholdSeconds", thresholdSeconds);
        ReflectionTestUtils.setField(disconnectMonitor, "startedAt", Instant.now().minusSeconds(thresholdSeconds + 1));
    }

    @Nested
    @DisplayName("Describe: detectDisconnected 메서드는")
    class Describe_with_detectDisconnected {

        @Nested
        @DisplayName("Context: 임계값을 넘긴 피보호자가 있으면")
        class Context_with_expired_ward {

            @Test
            @DisplayName("It : 마지막 정상 연결 시각을 담아 DisconnectDetected를 발행한다")
            void it_publishes_event() {
                //given
                given(presenceRepository.findDisconnected(any(Instant.class)))
                        .willReturn(Map.of(wardId, lastSync));

                //when
                disconnectMonitor.detectDisconnected();

                //then - 푸시 제목에는 감지한 시각이 아니라 끊긴 시각이 들어가야 한다
                ArgumentCaptor<DisconnectDetected> captor = ArgumentCaptor.forClass(DisconnectDetected.class);
                verify(eventPublisher).publishEvent(captor.capture());

                assertThat(captor.getValue()).isEqualTo(new DisconnectDetected(wardId, lastSync));
            }

            @Test
            @DisplayName("It : 발행한 뒤에 감시 목록에서 지운다")
            void it_unwatches_after_publishing() {
                //given
                given(presenceRepository.findDisconnected(any(Instant.class)))
                        .willReturn(Map.of(wardId, lastSync));

                //when
                disconnectMonitor.detectDisconnected();

                //then - 순서가 뒤집혀 먼저 지우면, 발행이 터졌을 때 대상이 사라져 영영 못 알린다
                InOrder order = inOrder(eventPublisher, presenceRepository);
                order.verify(eventPublisher).publishEvent(any(DisconnectDetected.class));
                order.verify(presenceRepository).unwatch(wardId);
            }

            @Test
            @DisplayName("It : 임계값만큼 과거인 시각으로 조회한다")
            void it_queries_with_threshold() {
                //given
                given(presenceRepository.findDisconnected(any(Instant.class))).willReturn(Map.of());
                Instant before = Instant.now().minusSeconds(thresholdSeconds);

                //when
                disconnectMonitor.detectDisconnected();

                //then
                ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
                verify(presenceRepository).findDisconnected(captor.capture());

                assertThat(captor.getValue()).isBetween(before, Instant.now().minusSeconds(thresholdSeconds - 1));
            }
        }

        @Nested
        @DisplayName("Context: 대상이 여러 명이면")
        class Context_with_multiple_wards {

            @Test
            @DisplayName("It : 각각 발행하고 각각 지운다")
            void it_handles_each() {
                //given
                Map<Long, Instant> expired = new LinkedHashMap<>();
                expired.put(wardId, lastSync);
                expired.put(otherWardId, lastSync.minusSeconds(60));
                given(presenceRepository.findDisconnected(any(Instant.class))).willReturn(expired);

                //when
                disconnectMonitor.detectDisconnected();

                //then
                verify(eventPublisher).publishEvent(new DisconnectDetected(wardId, lastSync));
                verify(eventPublisher).publishEvent(new DisconnectDetected(otherWardId, lastSync.minusSeconds(60)));
                verify(presenceRepository).unwatch(wardId);
                verify(presenceRepository).unwatch(otherWardId);
            }
        }

        @Nested
        @DisplayName("Context: 임계값을 넘긴 피보호자가 없으면")
        class Context_without_expired_ward {

            @Test
            @DisplayName("It : 아무것도 발행하지 않는다")
            void it_does_nothing() {
                //given
                given(presenceRepository.findDisconnected(any(Instant.class))).willReturn(Map.of());

                //when
                disconnectMonitor.detectDisconnected();

                //then
                verifyNoInteractions(eventPublisher);
            }
        }

        @Nested
        @DisplayName("Context: 기동한 지 임계값만큼 지나지 않았으면")
        class Context_within_startup_grace {

            @Test
            @DisplayName("It : 조회조차 하지 않는다")
            void it_skips_entirely() {
                //given - 방금 재시작한 상태. 이 유예가 없으면 배포 때마다 전원에게 '연결 끊김'이 나간다.
                // 서버가 내려가 있는 동안에는 heartbeat가 안 오므로 전원이 만료돼 보인다
                ReflectionTestUtils.setField(disconnectMonitor, "startedAt", Instant.now());

                //when
                disconnectMonitor.detectDisconnected();

                //then
                verifyNoInteractions(presenceRepository, eventPublisher);
            }
        }
    }
}
