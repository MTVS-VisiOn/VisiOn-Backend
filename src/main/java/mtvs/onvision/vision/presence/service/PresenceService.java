package mtvs.onvision.vision.presence.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.presence.dto.HeartbeatRequest;
import mtvs.onvision.vision.presence.repository.PresenceRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PresenceService {
    private final PresenceRepository presenceRepository;
    private final ObjectMapper objectMapper;

    public void saveHeartBeat(HeartbeatRequest request, CurrentUser currentUser) {
        String json = objectMapper.writeValueAsString(request);
        presenceRepository.saveHeartbeat(currentUser.getId(), json);
    }
}
