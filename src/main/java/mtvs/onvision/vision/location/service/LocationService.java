package mtvs.onvision.vision.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.GeoUtils;
import mtvs.onvision.vision.location.domain.MovementStatus;
import mtvs.onvision.vision.location.dto.*;
import mtvs.onvision.vision.location.repository.LocationHistoryRepository;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
import mtvs.onvision.vision.presence.service.PresenceService;
import mtvs.onvision.vision.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {
    private final LocationHistoryRepository locationHistoryRepository;
    private final RealtimeLocationRepository realtimeLocationRepository;
    private final PresenceService presenceService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final RestClient tmapRestClient;

    private final String REVERSE_GEOCODING = "/tmap/geo/reversegeocoding";
    private final String POI_SEARCH = "/tmap/pois";

    public void receiveLocation(LocationRequest request, CurrentUser currentUser) {
        MovementStatus status = classifyMovement(request, currentUser.getId());
        LocationReport report = LocationReport.from(request, currentUser.getId(),status);
        String json = objectMapper.writeValueAsString(report);
        realtimeLocationRepository.saveLocation(currentUser.getId(), json);
    }

    public LastLocationResponse getLastLocation(CurrentUser currentUser) {
        Long wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        //좌표 구하기
        Optional<String> json  = realtimeLocationRepository.getLastLocation(wardId);
        if (json.isEmpty()) return null;

        LocationReport report = objectMapper.readValue(json.get(), LocationReport.class);
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

        return new LastLocationResponse(true, roadAddress, report.status().getMessage());
    }


    //주소검색 - 티맵 api 사용
    public LocationSearchResponse searchLocation(String keyword) {
        try {
            TmapPoiSearchResponse res = tmapRestClient.get()
                    .uri(
                            uriBuilder -> uriBuilder
                                    .path(POI_SEARCH)
                                    .queryParam("version", 1)
                                    .queryParam("searchKeyword", keyword)
                                    .queryParam("count", 10)
                                    .build())
                    .retrieve()  //응답 받아오기
                    .body(TmapPoiSearchResponse.class);
            TmapPoiSearchResponse.SearchPoiInfo poiInfo = res.searchPoiInfo();
            List<Poi> pois = poiInfo.pois().poi();
            List<LocationSearchInfo> infos = pois.stream().map(LocationSearchInfo::from).toList();
            return new LocationSearchResponse(poiInfo.totalCount(), poiInfo.count(), poiInfo.page(), infos);
        } catch (NullPointerException e) {
            log.info("TMap 호출 실패 keyword={}", keyword);
            return new LocationSearchResponse(0,0,0, List.of());
        } catch (Exception e) {
            log.error("TMap 호출 실패 type={}, cause={}", e.getClass().getName(), e.getCause(), e);
            throw new BusinessException(ErrorCode.TMAP_API_ERROR);
        }
    }

    //이동 상태 판별하기
    private MovementStatus classifyMovement(LocationRequest report, Long wardId) {
        Optional<String> preJson  = realtimeLocationRepository.getLastLocation(wardId);
        if (preJson.isEmpty()) return MovementStatus.UNKNOWN;

        LocationReport preReport = objectMapper.readValue(preJson.get(), LocationReport.class);
        //값이 0 이전이거나 5분이 넘어간경우 알수 없음
        long dtSec = Duration.between(preReport.recordedAt(), report.recordedAt()).getSeconds();
        if (dtSec <= 0 || dtSec > 300) return MovementStatus.UNKNOWN;

        double distance = GeoUtils.distanceMeters(report.latitude(), report.longitude(), preReport.latitude(), preReport.longitude());

        //오차 반경 안이면 멈춤으로 간주
        double errorRadius = nvl(preReport.accuracy()) + nvl(report.accuracy());
        if (distance <= errorRadius) return MovementStatus.STATIONARY;

        //아니라면 속도로 판별
        return bySpeed((float) (distance/dtSec));
    }

    private MovementStatus bySpeed(Float mps) {
        if (mps < 0.5) return MovementStatus.STATIONARY;
        if (mps < 2.8) return MovementStatus.ON_FOOT;
        return MovementStatus.IN_VEHICLE;
    }

    private double nvl(Float accuracy) {
        return accuracy != null ? accuracy : 20.0;         // 기본 오차 20m
    }

}
