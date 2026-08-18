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
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final RestClient tmapRestClient;

    private final String REVERSE_GEOCODING = "/tmap/geo/reversegeocoding";
    private final String POI_SEARCH = "/tmap/pois";

    public void receiveLocation(LocationRequest request, CurrentUser currentUser) {
        log.debug("Location write requested: userId={} role={} tokenType={} lat={} lon={} accuracy={} recordedAt={}",
                currentUser.getId(), currentUser.getRole(), currentUser.getTokenType(),
                request.latitude(), request.longitude(), request.accuracy(), request.recordedAt());
        MovementStatus status = classifyMovement(request, currentUser.getId());
        LocationReport report = LocationReport.from(request, currentUser.getId(),status);
        String json = objectMapper.writeValueAsString(report);
        realtimeLocationRepository.saveLocation(currentUser.getId(), json);
        log.debug("Location write stored: key=location:latest:{} status={}", currentUser.getId(), status);
    }

    public LastLocationResponse getLastLocation(CurrentUser currentUser) {
        log.debug("Location read requested: userId={} role={} tokenType={}",
                currentUser.getId(), currentUser.getRole(), currentUser.getTokenType());
        Long wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        //좌표 구하기
        Optional<String> json  = realtimeLocationRepository.getLastLocation(wardId);
        log.debug("Location lookup: key=location:latest:{} hit={}", wardId, json.isPresent());
        if (json.isEmpty()) {
            //200 + data=null 로 나간다. 앱에서는 '기록 없음'과 '서버 오류'가 구분되지 않는다
            log.debug("Location read: 최근 위치 없음 — guardianId={} wardId={}", currentUser.getId(), wardId);
            return null;
        }

        LocationReport report = objectMapper.readValue(json.get(), LocationReport.class);
        Double latitude = report.latitude();
        Double longitude = report.longitude();

        String roadAddress = getRoadAddress(latitude, longitude);

        log.debug("Location read: wardId={} lat={} lon={} status={} recordedAt={} roadAddress={}",
                wardId, latitude, longitude, report.
                        status(), report.recordedAt(), roadAddress);
        return new LastLocationResponse(latitude, longitude, roadAddress, report.status().getMessage() ,report.recordedAt());
    }

    /**
     * 좌표를 주소로 바꾼다. 실패하면 예외를 던지지 않고 {@code null}을 준다.
     * <p>
     * 주소는 부가 정보이고 좌표는 이미 확보돼 있다. 여기서 던지면 TMap 장애 하나가
     * 위치 조회 전체(500)와 장애물 알림 저장까지 같이 죽인다. {@code alerts.address}는
     * nullable이라 주소 없이도 알림은 남는다.
     */
    public String getRoadAddress(Double latitude, Double longitude) {
        //티맵 API로 주소 찾기
        try {
            TmapReverseGeoCodingResponse res = tmapRestClient.get()
                    .uri(
                            uriBuilder -> uriBuilder
                                    .path(REVERSE_GEOCODING)
                                    .queryParam("version",1)
                                    .queryParam("lat", latitude)
                                    .queryParam("lon", longitude)
                                    .queryParam("addressType","A10")
                                    .build())
                    .retrieve()  //응답 받아오기
                    .body(TmapReverseGeoCodingResponse.class);
            String presentAddress = res == null || res.addressInfo() == null ? null : res.addressInfo().fullAddress();
            log.debug("TMap reverse geocoding: lat={} lon={} fullAddress={}", latitude, longitude, presentAddress);
            if (presentAddress == null) {
                log.warn("TMap 역지오코딩 주소 없음: lat={} lon={}", latitude, longitude);
                return null;
            }
            return presentAddress.substring(presentAddress.lastIndexOf(",") + 1);
        } catch (Exception e) {
            log.warn("TMap 역지오코딩 실패: lat={} lon={} type={} message={}",
                    latitude, longitude, e.getClass().getName(), e.getMessage());
            return null;
        }
    }

    //주소검색 - 티맵 api 사용
    public LocationSearchResponse searchLocation(String keyword, CurrentUser currentUser) {
        //최근 위치 받아오기
        CoordinateInfo center = getSearchCenter(currentUser.getId());
        //현재 위치 정보 없으면 null로 응답
        if (center == null) return new LocationSearchResponse(0,0,0, null, List.of());
        try {
            TmapPoiSearchResponse res = tmapRestClient.get()
                    .uri(uriBuilder -> {
                        return uriBuilder.path(POI_SEARCH)
                                .queryParam("version", 1)
                                .queryParam("searchKeyword", keyword)
                                .queryParam("count", 10)
                                .queryParam("centerLat", center.latitude())
                                .queryParam("centerLon", center.longitude())
                                .queryParam("searchtypCd", "R")
                                .queryParam("radius", 0)        // 0 = 전국. 거리순 정렬만 쓰고 반경으로 거르지는 않는다
                                .build();
                    })
                    .retrieve()
                    .body(TmapPoiSearchResponse.class);

            TmapPoiSearchResponse.SearchPoiInfo poiInfo = res.searchPoiInfo();
            List<Poi> pois = poiInfo.pois().poi();
            List<LocationSearchInfo> infos = pois.stream().map(LocationSearchInfo::from).toList();
            log.debug("Location search: keyword={} totalCount={} returned={}", keyword, poiInfo.totalCount(), infos.size());
            return new LocationSearchResponse(poiInfo.totalCount(), poiInfo.count(), poiInfo.page(), center, infos);
        } catch (NullPointerException e) {
            log.info("TMap 호출 실패 keyword={}", keyword);
            return new LocationSearchResponse(0,0,0, center, List.of());
        } catch (Exception e) {
            log.error("TMap 호출 실패 type={}, cause={}", e.getClass().getName(), e.getCause(), e);
            throw new BusinessException(ErrorCode.TMAP_API_ERROR);
        }
    }

    //이동 상태 판별하기
    private MovementStatus classifyMovement(LocationRequest report, Long wardId) {
        Optional<String> preJson  = realtimeLocationRepository.getLastLocation(wardId);
        if (preJson.isEmpty()) {
            log.debug("Movement classify: 직전 좌표 없음 — wardId={} (첫 보고이거나 latest 키 TTL 만료)", wardId);
            return MovementStatus.UNKNOWN;
        }

        LocationReport preReport = objectMapper.readValue(preJson.get(), LocationReport.class);
        //값이 0 이전이거나 5분이 넘어간경우 알수 없음
        long dtSec = Duration.between(preReport.recordedAt(), report.recordedAt()).getSeconds();
        if (dtSec <= 0 || dtSec > 300) {
            log.debug("Movement classify: 간격 이상 — wardId={} dtSec={} (0 이하면 시각 역전, 300 초과면 보고 끊김)", wardId, dtSec);
            return MovementStatus.UNKNOWN;
        }

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

    /** 최근 위치가 없거나(30분 TTL 만료·첫 실행) 읽지 못하면 null — 호출부가 검색을 건너뛴다 */
    private CoordinateInfo getSearchCenter(Long wardId) {
        Optional<String> json = realtimeLocationRepository.getLastLocation(wardId);
        if (json.isEmpty()) {
            log.debug("검색 중심 좌표 없음 — wardId={}", wardId);
            return null;
        }
        try {
            LocationReport report = objectMapper.readValue(json.get(), LocationReport.class);
            if (report.latitude() == null || report.longitude() == null) return null;
            return new CoordinateInfo(report.latitude(), report.longitude());
        } catch (Exception e) {
            log.warn("최근 위치 파싱 실패 wardId={} message={}", wardId, e.getMessage());
            return null;
        }
    }

}
