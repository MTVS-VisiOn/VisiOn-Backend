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

    /** 이 아래 속도는 정지로 본다. 보호대상자의 느린 보행(시속 1.08km)까지 이동으로 잡으려고 낮춰 잡았다 */
    private static final float WALK_MIN_MPS = 0.3f;
    /** 이 위 속도는 차량으로 본다. 시속 10.08km */
    private static final float VEHICLE_MIN_MPS = 2.8f;
    /** 정지 확정까지 기다리는 최소 시간 */
    private static final long STATIONARY_CONFIRM_MIN_SEC = 20;
    /** 정지 확정까지 기다리는 최대 시간. 실내처럼 오차가 수백 m면 비례 계산이 26분까지 늘어난다 */
    private static final long STATIONARY_CONFIRM_MAX_SEC = 120;
    /** 보고 간격이 이보다 벌어지면 그 사이에 무슨 일이 있었는지 알 수 없다 */
    private static final long REPORT_GAP_LIMIT_SEC = 300;

    public void receiveLocation(LocationRequest request, CurrentUser currentUser) {
        log.debug("Location write requested: userId={} role={} tokenType={} lat={} lon={} accuracy={} recordedAt={}",
                currentUser.getId(), currentUser.getRole(), currentUser.getTokenType(),
                request.latitude(), request.longitude(), request.accuracy(), request.recordedAt());
        Movement movement = classifyMovement(request, readPrevious(currentUser.getId()), currentUser.getId());
        LocationReport report = LocationReport.from(request, currentUser.getId(), movement.status(), movement.anchor());
        String json = objectMapper.writeValueAsString(report);
        realtimeLocationRepository.saveLocation(currentUser.getId(), json);
        log.debug("Location write stored: key=location:latest:{} status={} anchorAt={}",
                currentUser.getId(), movement.status(), movement.anchor().recordedAt());
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

    /**
     * 이동 상태를 판별한다.
     *
     * 직전 보고가 아니라 <b>앵커</b>(마지막으로 판정을 내린 지점)와 비교한다. 보고는 3초 간격인데
     * 그 사이 걸어서 움직이는 거리는 GPS 오차 반경보다 작아서, 직전 보고와만 비교하면 어떤 보행
     * 속도로도 반경을 못 넘어 영원히 STATIONARY가 된다. 2026-08-24 실기기 검증에서 실제로 그렇게
     * 나왔다 — accuracy 3m대에 3초 간격이면 반경 6m를 넘으려면 시속 7.9km(달리기)가 필요했다.
     *
     * 앵커를 반경 밖으로 나갈 때까지 들고 가므로 보고 주기와 무관하게 같은 결과가 나온다.
     */
    private Movement classifyMovement(LocationRequest report, LocationReport previous, Long wardId) {
        if (previous == null) {
            log.debug("Movement classify: 직전 좌표 없음 — wardId={} (첫 보고이거나 latest 키 TTL 만료)", wardId);
            return new Movement(MovementStatus.UNKNOWN, MovementAnchor.of(report));
        }

        //값이 0 이전이거나 5분이 넘어간경우 알수 없음
        long dtSec = Duration.between(previous.recordedAt(), report.recordedAt()).getSeconds();
        if (dtSec <= 0 || dtSec > REPORT_GAP_LIMIT_SEC) {
            log.debug("Movement classify: 간격 이상 — wardId={} dtSec={} (0 이하면 시각 역전, {} 초과면 보고 끊김)",
                    wardId, dtSec, REPORT_GAP_LIMIT_SEC);
            return new Movement(MovementStatus.UNKNOWN, MovementAnchor.of(report));
        }

        //앵커가 없는 옛 형식(이 필드가 생기기 전에 저장된 값)은 직전 보고 자체를 기준점으로 쓴다
        MovementAnchor anchor = previous.anchor() != null ? previous.anchor() : MovementAnchor.of(previous);

        double distance = GeoUtils.distanceMeters(report.latitude(), report.longitude(), anchor.latitude(), anchor.longitude());
        double errorRadius = nvl(anchor.accuracy()) + nvl(report.accuracy());
        long sinceAnchor = Duration.between(anchor.recordedAt(), report.recordedAt()).getSeconds();

        //오차 반경을 벗어났다 — 실제로 움직인 것이므로 속도로 판별하고 기준점을 옮긴다
        if (distance > errorRadius) {
            MovementStatus status = bySpeed((float) (distance / Math.max(sinceAnchor, 1)));
            log.debug("Movement classify: 앵커 이탈 — wardId={} distance={} errorRadius={} sinceAnchor={}s status={}",
                    wardId, distance, errorRadius, sinceAnchor, status);
            return new Movement(status, MovementAnchor.of(report));
        }

        //반경 안이지만 시간이 충분히 지났다 — 걷고 있었다면 진작 벗어났을 거리다
        long confirmSec = stationaryConfirmSec(errorRadius);
        if (sinceAnchor >= confirmSec) {
            log.debug("Movement classify: 정지 확정 — wardId={} distance={} errorRadius={} sinceAnchor={}s confirmSec={}",
                    wardId, distance, errorRadius, sinceAnchor, confirmSec);
            return new Movement(MovementStatus.STATIONARY, MovementAnchor.of(report));
        }

        //아직 판정할 근거가 없다. 기준점을 그대로 들고 가며 직전 판정을 유지한다
        return new Movement(previous.status(), anchor);
    }

    /**
     * 오차 반경 안에 머무를 때 정지로 확정하기까지 기다리는 시간.
     *
     * 가장 느린 보행({@link #WALK_MIN_MPS})이라도 이 시간이면 반경을 벗어났을 만큼 기다린다.
     * 고정값으로 두면 오차 반경이 클 때 반경을 벗어나기도 전에 정지가 먼저 확정된다 —
     * accuracy 15m(반경 30m)면 시속 4.8km로 걸어도 21초에 STATIONARY로 잘못 판정됐다.
     * 실기기 실외 정지 샘플이 13.8m였으므로 이 구간은 실제로 밟힌다.
     */
    private long stationaryConfirmSec(double errorRadius) {
        long needed = (long) Math.ceil(errorRadius / WALK_MIN_MPS);
        return Math.max(STATIONARY_CONFIRM_MIN_SEC, Math.min(STATIONARY_CONFIRM_MAX_SEC, needed));
    }

    private MovementStatus bySpeed(float mps) {
        if (mps < WALK_MIN_MPS) return MovementStatus.STATIONARY;
        if (mps < VEHICLE_MIN_MPS) return MovementStatus.ON_FOOT;
        return MovementStatus.IN_VEHICLE;
    }

    /** 최근 위치를 읽는다. 없으면 null — 첫 보고이거나 30분 TTL이 지난 경우다 */
    private LocationReport readPrevious(Long wardId) {
        return realtimeLocationRepository.getLastLocation(wardId)
                .map(json -> objectMapper.readValue(json, LocationReport.class))
                .orElse(null);
    }

    /** 판정 결과와, 다음 판정이 기준으로 삼을 앵커 */
    private record Movement(MovementStatus status, MovementAnchor anchor) {}

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
