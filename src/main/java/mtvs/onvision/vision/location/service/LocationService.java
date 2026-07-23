package mtvs.onvision.vision.location.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.location.dto.LocationReport;
import mtvs.onvision.vision.location.dto.LocationRequest;
import mtvs.onvision.vision.location.repository.LocationHistoryRepository;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class LocationService {
    private final LocationHistoryRepository locationHistoryRepository;
    private final RealtimeLocationRepository realtimeLocationRepository;
    private final ObjectMapper objectMapper;

    public void receiveLocation(LocationRequest request, CurrentUser currentUser) {
        LocationReport report = LocationReport.from(request, currentUser.getId());
        String json = objectMapper.writeValueAsString(report);
        realtimeLocationRepository.saveLocation(currentUser.getId(), json);
    }
}
