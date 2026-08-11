package mtvs.onvision.vision.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.location.dto.LocationReport;
import mtvs.onvision.vision.location.repository.LocationHistoryJdbcRepository;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Redis 버퍼에 쌓인 위치를 DB로 옮긴다.
 *
 * 위치는 3초 간격으로 들어와서 건별 INSERT를 하면 쓰기가 계속 붙는다.
 * `receiveLocation`은 Redis에 넣기만 하고, 실제 저장은 여기서 배치로 몰아친다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationHistoryService {

    private final RealtimeLocationRepository realtimeLocationRepository;
    private final LocationHistoryJdbcRepository locationHistoryJdbcRepository;
    private final ObjectMapper objectMapper;

    @Value("${location.flush.batch-size}")
    private int batchSize;

    @Value("${location.flush.max-loops}")
    private int maxLoops;

    /**
     * 버퍼가 빌 때까지 배치로 옮겨 담는다.
     *
     * **트랜잭션을 걸지 않는다.** 삽입이 터지면 처리 중 큐가 남아 다음 주기에 통째로 재시도되고,
     * 이미 들어간 행은 `ON CONFLICT`가 걸러낸다. 그래서 "커밋됐는지"를 알 필요가 없고
     * `TransactionSynchronization`도 필요 없다.
     *
     * 한 틱에 한 배치만 넣으면 유입량이 배치 크기를 넘는 순간 버퍼가 영원히 안 줄어든다.
     * 그래서 비워질 때까지 돌리되, 한 틱이 무한정 도는 것을 `maxLoops`로 막는다.
     */
    public void flush() {
        for (int loop = 0; loop < maxLoops; loop++) {
            List<String> raw = realtimeLocationRepository.moveToProcessing(batchSize);
            if (raw.isEmpty()) return;

            List<LocationReport> batch = raw.stream()
                    .map(json -> objectMapper.readValue(json, LocationReport.class))
                    .toList();

            int inserted = locationHistoryJdbcRepository.batchInsert(batch);
            realtimeLocationRepository.clearProcessing();   // 여기 도달 = 커밋 완료

            if (inserted < batch.size()) {
                // 재시도로 다시 들어온 행이 있었다는 뜻. 반복되면 앱이 계속 죽고 있는 것이다
                log.info("Location flushed with duplicates: popped={} inserted={}", batch.size(), inserted);
            }
            if (batch.size() < batchSize) return;   // 버퍼가 비었다
        }
        log.warn("Location flush hit max loops. 유입이 배치 처리량을 넘고 있다 — batchSize={} maxLoops={}",
                batchSize, maxLoops);
    }
}
