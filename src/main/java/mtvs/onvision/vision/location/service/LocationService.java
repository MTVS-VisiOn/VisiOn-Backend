package mtvs.onvision.vision.location.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.location.dto.LastLocationResponse;
import mtvs.onvision.vision.location.dto.LocationReport;
import mtvs.onvision.vision.location.dto.LocationRequest;
import mtvs.onvision.vision.location.dto.TmapReverseGeoCodingResponse;
import mtvs.onvision.vision.location.repository.LocationHistoryRepository;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
import mtvs.onvision.vision.presence.service.PresenceService;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class LocationService {
    private final LocationHistoryRepository locationHistoryRepository;
    private final RealtimeLocationRepository realtimeLocationRepository;
    private final PresenceService presenceService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final RestClient tmapRestClient;

    public final String REVERSE_GEOCODING = "/tmap/geo/reversegeocoding";

    @Value("${tmap.app-key}")
    private String appKey;

    public void receiveLocation(LocationRequest request, CurrentUser currentUser) {
        LocationReport report = LocationReport.from(request, currentUser.getId());
        String json = objectMapper.writeValueAsString(report);
        realtimeLocationRepository.saveLocation(currentUser.getId(), json);
    }

    public LastLocationResponse getLastLocation(CurrentUser currentUser) {
        Long wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        boolean isConnected = presenceService.getIsConnected(wardId);
        // 연결 상태가 아니라면 현재 위치를 내보낼 수 없음
        if (!isConnected) return new LastLocationResponse(false, null, null);
        //좌표 구하기
        String json  = realtimeLocationRepository.getLastLocation(wardId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_LAST_LOCATION));
        LocationReport report = objectMapper.readValue(json, LocationReport.class);
        Double latitude = report.latitude();
        Double longitude = report.longitude();

        //티맵 API로 주소 찾기
        TmapReverseGeoCodingResponse res = tmapRestClient.get()
                .uri(
                uriBuilder -> uriBuilder
                        .path(REVERSE_GEOCODING)
                        .queryParam("version",1)
                        .queryParam("lat",latitude)
                        .queryParam("lon",longitude)
                        .queryParam("addressType","A10")
                        .build())
                .retrieve()  //응답 받아오기
                .body(TmapReverseGeoCodingResponse.class);
        String presentAddress = res.addressInfo().fullAddress();
        String roadAddress = presentAddress.substring(presentAddress.lastIndexOf(",") + 1);


        return new LastLocationResponse(true, roadAddress, "");
    }
}
