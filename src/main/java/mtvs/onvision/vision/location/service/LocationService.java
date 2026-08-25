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
import java.time.Instant;
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
    /**
     * 차량으로 확정하기까지 필요한 연속 판정 횟수.
     *
     * 2026-08-25 실기기 검증에서 오판 3건은 연속 1·1·2회로 끝났고, 실제 버스 탑승 2건은
     * 연속 5회·3회였다. GPS가 튄 것은 몇 초 만에 제자리로 돌아오지만 차량은 계속 간다.
     */
    private static final int VEHICLE_CONFIRM_COUNT = 3;
    /**
     * 이 시간을 넘겨 잰 속도는 판정 근거로 쓰지 않는다.
     *
     * 앵커에서 잰 거리를 경과 시간으로 나누면 평균 속도가 나오는데, 그 사이 보고가 끊겼거나
     * 단말이 같은 좌표만 되풀이해 보냈다면 실제로는 마지막 몇 초에 몰아서 이동한 것일 수 있다.
     * 2026-08-25 검증에서 버스가 187m를 갔는데 앵커가 102초 묵어 있어 1.83 m/s(보행)로 계산됐다.
     * 같은 날 정상 판정의 앵커 나이는 전부 46초 이하였다.
     */
    private static final long SPEED_BASELINE_MAX_SEC = 60;
    /**
     * 방금 전까지 차량이었다면 재확정에 필요한 연속 판정 횟수.
     *
     * 2026-08-25 퇴근시간대 재검증에서 정체 구간이 통째로 빠졌다. 실제로는 버스 안이었던
     * 4분 51초가 ON_FOOT으로 나갔는데, 서행으로 속도가 보행 수준까지 떨어진 데다 신호 정차가
     * 잦아 3연속을 채우기 전에 계속 횟수가 0으로 밀렸기 때문이다.
     *
     * 그렇다고 {@link #VEHICLE_CONFIRM_COUNT}를 낮추면 처음 타는 순간의 오판이 늘어난다.
     * 그래서 진입 문턱은 3으로 두고 재진입만 완화한다 — 같은 날 오판 3건은 모두 하차 직후
     * 180초 안에 났지만 전부 연속 1회로 끝났으므로 2에서는 걸러진다.
     */
    private static final int VEHICLE_REENTRY_COUNT = 2;
    /** 하차 뒤 이 시간 안에 다시 차량 속도가 나오면 같은 이동으로 본다 */
    private static final long VEHICLE_REENTRY_SEC = 180;

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
     *
     * 차량 판정만은 한 번의 속도 계산으로 확정하지 않는다. GPS가 한두 보고 동안 수십 m 튀면
     * 걷는 중에도 시속 30~40km가 찍히기 때문이다. 연속 VEHICLE_CONFIRM_COUNT회를 채우기
     * 전까지는 "움직이고 있다"까지만 인정해 ON_FOOT으로 내보낸다.
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
            boolean resolvable = sinceAnchor <= SPEED_BASELINE_MAX_SEC;
            MovementStatus measured = bySpeed((float) (distance / Math.max(sinceAnchor, 1)));
            int vehicleStreak = nextVehicleStreak(anchor.vehicleStreak(), measured, resolvable);
            int confirmCount = confirmCountFor(anchor.vehicleExitAt(), report.recordedAt());
            MovementStatus status = publish(measured, vehicleStreak, confirmCount);
            Instant vehicleExitAt = nextVehicleExitAt(anchor.vehicleExitAt(), previous.status(), status, report.recordedAt());
            log.debug("Movement classify: 앵커 이탈 — wardId={} distance={} errorRadius={} sinceAnchor={}s measured={} vehicleStreak={} confirmCount={} status={}",
                    wardId, distance, errorRadius, sinceAnchor, measured, vehicleStreak, confirmCount, status);
            return new Movement(status, MovementAnchor.of(report, vehicleStreak, vehicleExitAt));
        }

        //반경 안이지만 시간이 충분히 지났다 — 걷고 있었다면 진작 벗어났을 거리다
        long confirmSec = stationaryConfirmSec(errorRadius);
        if (sinceAnchor >= confirmSec) {
            //정지로 확정되면 차량에서도 내려온 것이다. 하차 시각을 남겨 재출발 때 재진입 문턱을 낮춘다
            Instant vehicleExitAt = nextVehicleExitAt(anchor.vehicleExitAt(), previous.status(),
                    MovementStatus.STATIONARY, report.recordedAt());
            log.debug("Movement classify: 정지 확정 — wardId={} distance={} errorRadius={} sinceAnchor={}s confirmSec={}",
                    wardId, distance, errorRadius, sinceAnchor, confirmSec);
            return new Movement(MovementStatus.STATIONARY, MovementAnchor.of(report, 0, vehicleExitAt));
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

    /**
     * 연속으로 차량 속도가 나온 횟수를 갱신한다.
     *
     * 속도를 신뢰할 수 없는 구간에서는 올리지도 내리지도 않는다. 근거가 없다는 것이지 반대
     * 근거가 생긴 것이 아니기 때문이다. 여기서 0으로 밀면 보고가 한 번 끊길 때마다 달리던
     * 차가 도보로 떨어진다 — 2026-08-25 검증에서 실제로 그렇게 나왔다.
     */
    private int nextVehicleStreak(int current, MovementStatus measured, boolean resolvable) {
        if (!resolvable) return current;
        return measured == MovementStatus.IN_VEHICLE ? current + 1 : 0;
    }

    /**
     * 실제로 내보낼 상태.
     *
     * 차량 속도가 나왔지만 아직 연속 횟수를 못 채웠으면 ON_FOOT으로 낮춘다. 오차 반경을 벗어난
     * 것은 사실이라 움직이고 있는 것은 맞고, 다만 차량인지가 아직 확실하지 않을 뿐이다.
     *
     * 반대로 이미 차량으로 확정된 뒤에 보행 속도가 한 번 나왔다고 바로 내려오지도 않는다.
     * 그 판정이 믿을 만한 구간에서 나왔다면 연속 횟수가 이미 0으로 밀려 여기까지 오지 않는다.
     */
    private MovementStatus publish(MovementStatus measured, int vehicleStreak, int confirmCount) {
        if (vehicleStreak >= confirmCount) return MovementStatus.IN_VEHICLE;
        if (measured == MovementStatus.IN_VEHICLE) return MovementStatus.ON_FOOT;
        return measured;
    }

    /**
     * 이번 보고에서 차량으로 확정하는 데 필요한 연속 횟수.
     *
     * 처음 타는 것과 방금 내렸다 다시 타는 것은 근거의 무게가 다르다. 하차 직후라면 아직 차 안일
     * 가능성이 남아 있으므로 문턱을 낮춘다. 하차 이력이 없거나 오래됐으면 원래대로 3회를 채워야 한다.
     */
    private int confirmCountFor(Instant vehicleExitAt, Instant now) {
        if (vehicleExitAt == null) return VEHICLE_CONFIRM_COUNT;
        long sinceExit = Duration.between(vehicleExitAt, now).getSeconds();
        boolean recent = sinceExit >= 0 && sinceExit <= VEHICLE_REENTRY_SEC;
        return recent ? VEHICLE_REENTRY_COUNT : VEHICLE_CONFIRM_COUNT;
    }

    /**
     * 마지막으로 차량에서 내려온 시각을 갱신한다.
     *
     * 차량으로 확정된 동안에는 {@code null}이다. 아직 내려오지 않았기 때문이고, 그래야 다음에
     * 내려올 때 그 시각이 정확히 기록된다.
     */
    private Instant nextVehicleExitAt(Instant current, MovementStatus previous, MovementStatus status, Instant now) {
        if (status == MovementStatus.IN_VEHICLE) return null;
        if (previous == MovementStatus.IN_VEHICLE) return now;
        return current;
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
