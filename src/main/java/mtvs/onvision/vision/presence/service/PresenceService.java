package mtvs.onvision.vision.presence.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.presence.domain.PresenceType;
import mtvs.onvision.vision.presence.dto.HeartbeatRequest;
import mtvs.onvision.vision.presence.dto.PresenceResponse;
import mtvs.onvision.vision.presence.repository.PresenceRepository;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PresenceService {
    private final PresenceRepository presenceRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public void saveHeartBeat(HeartbeatRequest request, CurrentUser currentUser) {
        String json = objectMapper.writeValueAsString(request);
        presenceRepository.saveHeartbeat(currentUser.getId(), json);
    }

    @Transactional
    public PresenceResponse getWardPresence(CurrentUser currentUser) {
        //피보호자의 아이디 조회
        Long wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        //마지막 연결 상태가 존재하는지 확인
        Optional<String> json = presenceRepository.getLastHeartbeat(wardId);
        //상태값 없으면 연경x
        if (json.isEmpty())
            return new PresenceResponse(null, false, PresenceType.NOT_FOUND.getDescription());
        //있으면 연결상태 판별
        //마지막 동기화 시간이 2분 이내 인지와 네트워크가 연결된 상태인지 확인
        HeartbeatRequest heartbeat = objectMapper.readValue(json.get(), HeartbeatRequest.class);
        boolean isRecent = heartbeat.lastSync().isAfter(Instant.now().minusSeconds(120));
        boolean networkConnected = heartbeat.network().connected();
        PresenceType status;
        if (isRecent) {
            if (networkConnected) status = PresenceType.NORMAL;
            else status = PresenceType.NOT_NETWORK;
        } else {
            if (networkConnected) status = PresenceType.NOT_NETWORK;
            else status = PresenceType.NOT_FOUND;
        }
        return new PresenceResponse(heartbeat.battery(),  heartbeat.deviceConnected(), status.getDescription());
    }
}
