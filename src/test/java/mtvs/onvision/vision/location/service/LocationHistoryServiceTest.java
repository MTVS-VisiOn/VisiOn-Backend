package mtvs.onvision.vision.location.service;

import mtvs.onvision.vision.location.domain.MovementStatus;
import mtvs.onvision.vision.location.dto.LocationReport;
import mtvs.onvision.vision.location.repository.LocationHistoryJdbcRepository;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationHistoryService의")
class LocationHistoryServiceTest {

    @InjectMocks
    private LocationHistoryService locationHistoryService;

    @Mock
    private RealtimeLocationRepository realtimeLocationRepository;

    @Mock
    private LocationHistoryJdbcRepository locationHistoryJdbcRepository;

    /** 진짜 매퍼를 쓴다. 버퍼에 든 JSON을 되읽는 게 이 흐름에서 조용히 깨지는 자리다 */
    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ObjectMapper fixtureMapper = JsonMapper.builder().build();

    private static final int BATCH_SIZE = 3;
    private static final int MAX_LOOPS = 4;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(locationHistoryService, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(locationHistoryService, "maxLoops", MAX_LOOPS);
    }

    private String json(int seq) {
        return fixtureMapper.writeValueAsString(new LocationReport(
                1L, 37.413196 + seq * 0.0001, 127.098403, 12.5f, MovementStatus.ON_FOOT,
                Instant.parse("2026-08-11T05:00:00Z").plusSeconds(seq * 3L)));
    }

    private List<String> jsons(int count) {
        return IntStream.range(0, count).mapToObj(this::json).toList();
    }

    @Nested
    @DisplayName("Describe: flush 메서드는")
    class Describe_with_flush {

        @Nested
        @DisplayName("Context: 버퍼가 비어 있으면")
        class Context_with_empty_buffer {

            @Test
            @DisplayName("It : DB를 건드리지 않고 끝낸다")
            void it_does_nothing() {
                //given
                given(realtimeLocationRepository.moveToProcessing(BATCH_SIZE)).willReturn(List.of());

                //when
                locationHistoryService.flush();

                //then : 빈 배치로 batchUpdate를 부르면 왕복만 낭비한다
                verifyNoInteractions(locationHistoryJdbcRepository);
                verify(realtimeLocationRepository, never()).clearProcessing();
            }
        }

        @Nested
        @DisplayName("Context: 배치보다 적게 쌓여 있으면")
        class Context_with_partial_batch {

            @Test
            @DisplayName("It : 한 번만 넣고 멈춘다")
            void it_flushes_once() {
                //given : 2건 < batchSize 3 → 버퍼가 비었다는 신호다
                given(realtimeLocationRepository.moveToProcessing(BATCH_SIZE)).willReturn(jsons(2));
                given(locationHistoryJdbcRepository.batchInsert(anyList())).willReturn(2);

                //when
                locationHistoryService.flush();

                //then
                verify(realtimeLocationRepository, times(1)).moveToProcessing(BATCH_SIZE);
                verify(locationHistoryJdbcRepository, times(1)).batchInsert(anyList());
            }

            @Test
            @DisplayName("It : 버퍼의 JSON을 LocationReport로 되읽어 넘긴다")
            void it_deserializes_reports() {
                //given
                given(realtimeLocationRepository.moveToProcessing(BATCH_SIZE)).willReturn(jsons(1));
                given(locationHistoryJdbcRepository.batchInsert(anyList())).willReturn(1);

                //when
                locationHistoryService.flush();

                //then : forClass(List.class)는 제네릭을 못 지켜 unchecked 경고가 난다. captor()가 타입을 추론한다
                ArgumentCaptor<List<LocationReport>> captor = ArgumentCaptor.captor();
                verify(locationHistoryJdbcRepository).batchInsert(captor.capture());

                LocationReport report = captor.getValue().getFirst();
                assertThat(report.userId()).isEqualTo(1L);
                assertThat(report.latitude()).isEqualTo(37.413196);
                assertThat(report.status()).isEqualTo(MovementStatus.ON_FOOT);
                assertThat(report.recordedAt()).isEqualTo(Instant.parse("2026-08-11T05:00:00Z"));
            }

            @Test
            @DisplayName("It : INSERT가 끝난 뒤에 처리 중 큐를 지운다")
            void it_clears_after_insert() {
                //given
                given(realtimeLocationRepository.moveToProcessing(BATCH_SIZE)).willReturn(jsons(1));
                given(locationHistoryJdbcRepository.batchInsert(anyList())).willReturn(1);

                //when
                locationHistoryService.flush();

                //then : 순서가 뒤집히면 INSERT 실패가 그대로 유실이 된다
                var order = inOrder(locationHistoryJdbcRepository, realtimeLocationRepository);
                order.verify(locationHistoryJdbcRepository).batchInsert(anyList());
                order.verify(realtimeLocationRepository).clearProcessing();
            }
        }

        @Nested
        @DisplayName("Context: 배치를 꽉 채워 쌓여 있으면")
        class Context_with_backlog {

            @Test
            @DisplayName("It : 버퍼가 빌 때까지 반복한다")
            void it_drains_until_empty() {
                //given : 3건 → 3건 → 1건. 마지막이 batchSize 미만이라 거기서 멈춘다
                given(realtimeLocationRepository.moveToProcessing(BATCH_SIZE))
                        .willReturn(jsons(3), jsons(3), jsons(1));
                given(locationHistoryJdbcRepository.batchInsert(anyList())).willReturn(3, 3, 1);

                //when
                locationHistoryService.flush();

                //then : 한 틱에 한 배치만 넣으면 유입이 배치를 넘는 순간 버퍼가 영영 안 줄어든다
                verify(locationHistoryJdbcRepository, times(3)).batchInsert(anyList());
                verify(realtimeLocationRepository, times(3)).clearProcessing();
            }

            @Test
            @DisplayName("It : maxLoops를 넘기지 않는다")
            void it_stops_at_max_loops() {
                //given : 계속 꽉 찬 배치가 나와도 한 틱이 무한정 돌면 안 된다
                given(realtimeLocationRepository.moveToProcessing(BATCH_SIZE)).willReturn(jsons(3));
                given(locationHistoryJdbcRepository.batchInsert(anyList())).willReturn(3);

                //when
                locationHistoryService.flush();

                //then
                verify(locationHistoryJdbcRepository, times(MAX_LOOPS)).batchInsert(anyList());
            }
        }

        @Nested
        @DisplayName("Context: INSERT가 실패하면")
        class Context_with_insert_failure {

            @Test
            @DisplayName("It : 처리 중 큐를 지우지 않고 예외를 올린다")
            void it_keeps_processing_queue() {
                //given
                given(realtimeLocationRepository.moveToProcessing(BATCH_SIZE)).willReturn(jsons(2));
                willThrow(new RuntimeException("DB down"))
                        .given(locationHistoryJdbcRepository).batchInsert(anyList());

                //when&then : 큐에 남아야 다음 주기에 통째로 재시도된다
                assertThatThrownBy(() -> locationHistoryService.flush())
                        .isInstanceOf(RuntimeException.class);
                verify(realtimeLocationRepository, never()).clearProcessing();
            }
        }

        @Nested
        @DisplayName("Context: 재시도로 중복이 섞여 들어오면")
        class Context_with_duplicates {

            @Test
            @DisplayName("It : ON CONFLICT로 걸러진 건수와 무관하게 처리 중 큐를 지운다")
            void it_clears_even_when_skipped() {
                //given : 3건 넣었는데 1건만 들어감 = 2건은 이미 저장된 재시도분이다
                given(realtimeLocationRepository.moveToProcessing(BATCH_SIZE)).willReturn(jsons(2));
                given(locationHistoryJdbcRepository.batchInsert(anyList())).willReturn(0);

                //when
                locationHistoryService.flush();

                //then : 0건이어도 정상 처리다. 이미 DB에 있다는 뜻이라 큐를 비워야 한다
                verify(realtimeLocationRepository).clearProcessing();
            }
        }
    }
}
