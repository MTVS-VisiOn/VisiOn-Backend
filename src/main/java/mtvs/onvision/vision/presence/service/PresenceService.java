package mtvs.onvision.vision.presence.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.presence.domain.PresenceType;
import mtvs.onvision.vision.presence.dto.HeartbeatRequest;
import mtvs.onvision.vision.presence.dto.PresenceResponse;
import mtvs.onvision.vision.presence.event.LowBatteryDetected;
import mtvs.onvision.vision.presence.repository.PresenceRepository;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PresenceService {
    private final PresenceRepository presenceRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${presence.battery.thresholds}")
    private List<Integer> thresholds;

    // static이면 @Value가 주입되지 않아 0으로 남는다. getIsRecent가 항상 false가 된다
    @Value("${presence.disconnect.threshold-seconds}")
    private long thresholdSeconds;

    //heartbeat 저장, 배터리 부족시 경고 알림 보내기
    public void saveHeartBeat(HeartbeatRequest request, CurrentUser currentUser) {
        Integer previous = getPreviousBattery(currentUser.getId());   // 덮어쓰기 전에
        presenceRepository.saveHeartbeat(currentUser.getId(), objectMapper.writeValueAsString(request));
        if (request.network().connected()) {
            presenceRepository.markConnected(currentUser.getId(), request.lastSync());
        }

        if (hasCrossed(previous, request.battery())) {
            eventPublisher.publishEvent(new LowBatteryDetected(
                    currentUser.getId(), request.battery(), request.lastHeartbeat()));
        }
    }

    @Transactional
    public PresenceResponse getWardPresence(CurrentUser currentUser) {
        //피보호자의 아이디 조회
        Long wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        //마지막 연결 상태가 존재하는지 확인
        Optional<String> json = presenceRepository.getLastHeartbeat(wardId);
        //상태값 없으면 연경x
        if (json.isEmpty())
            return new PresenceResponse(null, false, false,  PresenceType.NOT_FOUND.getDescription());
        //있으면 연결상태 판별
        //마지막 동기화 시간이 2분 이내 인지와 네트워크가 연결된 상태인지 확인
        HeartbeatRequest heartbeat = objectMapper.readValue(json.get(), HeartbeatRequest.class);
        boolean isRecent = getIsRecent(heartbeat.lastSync());
        boolean networkConnected = heartbeat.network().connected();
        PresenceType status;
        if (isRecent) {
            if (networkConnected) status = PresenceType.NORMAL;
            else status = PresenceType.NOT_NETWORK;
        } else {
            if (networkConnected) status = PresenceType.DELAY_SYNC;
            else status = PresenceType.NOT_FOUND;
        }
        return new PresenceResponse(heartbeat.battery(),  heartbeat.deviceConnected(), networkConnected, status.getDescription());
    }

    public boolean getIsConnected(Long wardId) {
        Optional<String> json = presenceRepository.getLastHeartbeat(wardId);
        if (json.isEmpty()) return false;
        HeartbeatRequest heartbeat = objectMapper.readValue(json.get(), HeartbeatRequest.class);
        boolean isRecent = getIsRecent(heartbeat.lastSync());
        boolean networkConnected = heartbeat.network().connected();
        return isRecent && networkConnected;
    }

    private boolean getIsRecent(Instant lastSync) {
        return lastSync.isAfter(Instant.now().minusSeconds(thresholdSeconds));
    }

    /** 이번 heartbeat에서 임계값을 새로 내려갔는가 */
    private boolean hasCrossed(Integer previous, int current) {
        if (previous == null) return false;
        return thresholds.stream().anyMatch(t -> previous > t && current <= t);
    }

    private Integer getPreviousBattery(Long userId) {
        return presenceRepository.getLastHeartbeat(userId)
                .map(json -> objectMapper.readValue(json, HeartbeatRequest.class).battery())
                .orElse(null);
    }
}
