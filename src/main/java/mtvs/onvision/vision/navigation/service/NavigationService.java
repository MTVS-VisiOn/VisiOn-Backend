package mtvs.onvision.vision.navigation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.GeoUtils;
import mtvs.onvision.vision.location.domain.MovementStatus;
import mtvs.onvision.vision.location.dto.LocationReport;
import mtvs.onvision.vision.location.repository.RealtimeLocationRepository;
import mtvs.onvision.vision.navigation.domain.Route;
import mtvs.onvision.vision.navigation.domain.RouteStatus;
import mtvs.onvision.vision.navigation.domain.TransportMode;
import mtvs.onvision.vision.navigation.dto.*;
import mtvs.onvision.vision.navigation.repository.NavigationRepository;
import mtvs.onvision.vision.navigation.repository.RouteRepository;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mtvs.onvision.vision.common.util.AppTime.SEOUL;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {
    private final RestClient tmapRestClient;
    private final NavigationRepository navigationRepository;
    private final ObjectMapper objectMapper;
    private final RouteRepository routeRepository;

    private static final Pattern DISTANCE_TAIL = Pattern.compile("\\d+m 이동$");

    /** 경로 출발점으로 쓸 수 있는 반경 오차 상한(m). 실측 실외 15건이 전부 12m 이하, 픽스 없음이 55~117m다 */
    private static final float START_ACCURACY_MAX_M = 30f;
    /** 요청에 실려 온 좌표의 수명(초). 클라이언트가 요청 직전에 측정한 값이라 짧게 잡는다 */
    private static final long REQUEST_MAX_AGE_SEC = 20;
    /** 저장 좌표가 정지 상태일 때의 수명(초). 정지 중 보고 간격이 58~62초라 그 1.5배로 둔다 */
    private static final long CACHE_MAX_AGE_STATIONARY_SEC = 90;
    /** 저장 좌표가 이동 중일 때의 수명(초). 보행 중 보고 간격은 3~5초다 */
    private static final long CACHE_MAX_AGE_MOVING_SEC = 30;
    /** 폰 시계가 서버보다 앞설 때 오차로 봐주는 상한(초). 실측 스큐는 ±1.2초다 */
    private static final long CLOCK_SKEW_TOLERANCE_SEC = 5;
    private final UserService userService;
    private final RealtimeLocationRepository realtimeLocationRepository;
    private final RouteProgressCalculator routeProgressCalculator;


    //네비게이션 경로 찾기
    public NavigationSummary searchNavigation(NavigationPreRequest request, CurrentUser currentUser) {
        TransportMode mode = request.mode();
        log.debug("Navigation search requested: userId={} role={} mode={} start=({},{}) end=({},{})",
                currentUser.getId(), currentUser.getRole(), mode,
                request.start().latitude(), request.start().longitude(),
                request.end().latitude(), request.end().longitude());
        // 이동수단 검증이 먼저다. 출발 좌표 판정보다 뒤에 두면 잘못된 mode에 위치 오류가 나간다
        if (mode != TransportMode.WALK && mode != TransportMode.CAR) throw new BusinessException(ErrorCode.INVALID_TRANSFER);
        // 출발 좌표는 요청값 → 저장 위치 → 409 순으로 정한다. 규칙은 resolveStart 한 곳에만 둔다
        StartOrigin startOrigin = resolveStart(request, wardIdOf(currentUser));
        List<Double> requestedStart = startOrigin.coordinate();
        // 목적지는 보행자 입구점 우선. 규칙은 LocationInfo.routingCoordinate 한 곳에만 둔다
        List<Double> requestedEnd = request.end().routingCoordinate(mode);
        MultiValueMap<String, String> form = getStringStringMultiValueMap(request, mode, requestedStart, requestedEnd);
        try {
            if (mode == TransportMode.WALK) {
                //티맵에서 경로찾기
                TmapPedestrianResponse res = tmapRestClient.post()
                        .uri(
                                uriBuilder -> uriBuilder
                                        .path(mode.getPath())
                                        .queryParam("version", 1)
                                        .build())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()  //응답 받아오기
                        .body(TmapPedestrianResponse.class);
                // 요약 부분 생성
                List<TmapPedestrianResponse.Feature> raw = res.features();

                // 요약용 — totalDistance/totalTime은 RouteFeature에 없으니 원본에서 뽑는다
                TmapPedestrianResponse.Properties fStart = raw.stream()
                        .map(TmapPedestrianResponse.Feature::properties)
                        .filter(p -> p.pointType() == RouteStepType.SP)
                        .reduce((_, _) -> { throw new BusinessException(ErrorCode.TMAP_API_ERROR); })
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_TMAP_ROUTE));

                // 여기서 한 번 정규화
                Integer totalDistance = fStart.totalDistance();
                Integer totalTime = fStart.totalTime();
                List<RouteFeature> features = raw.stream().map(RouteFeature::from).toList();
                List<Double> snappedStart = snappedStartOf(features);

                List<RouteFeature> lines = features.stream()
                        .filter(f -> f.type() == GeometryType.LINE_STRING)
                        .toList();

                int crosswalkCount = countGroups(lines, FacilityType.CROSSWALK);
                int stairCount = countGroups(lines, FacilityType.STAIRS);
                int overpassCount = countGroups(lines, FacilityType.OVERPASS);
                int underpassCount = countGroups(lines, FacilityType.UNDERPASS);
                LocationInfo start = request.start();
                LocationInfo end = request.end();
                WalkSummaryResponse summary = new WalkSummaryResponse(
                        0,
                        request.mode(),
                        totalDistance, totalTime, crosswalkCount,
                        stairCount, overpassCount, underpassCount,
                        (start.nickname() == null || start.nickname().isBlank())? start.name(): start.nickname(),
                        start.address(), List.of(start.latitude(), start.longitude()),
                        (end.nickname() == null || end.nickname().isBlank())? end.name(): end.nickname(),
                        end.address(),List.of(end.latitude(), end.longitude())
                );

                // RoutStep 생성
                List<RouteStep> steps = toSteps(features, mode);

                //경로 redis 저장
                NavigationRouteReport report = new NavigationRouteReport(
                        summary, startOrigin.source(), startOrigin.accuracy(), startOrigin.recordedAt(),
                        requestedStart, snappedStart, snapDistanceM(requestedStart, snappedStart),
                        requestedEnd, steps);
                verifyRoutePath(report, mode);
                String json = objectMapper.writeValueAsString(report);
                navigationRepository.saveRoute(currentUser.getId(), mode.getPrefix(), json);
                log.debug("Navigation search done: mode={} totalDistance={} totalTime={} steps={} pathPoints={} snapDistanceM={} startSource={} startAccuracy={} entrance={} redisPrefix={}",
                        mode, totalDistance, totalTime, steps.size(), report.routePath().size(),
                        report.snapDistanceM(), startOrigin.source(), startOrigin.accuracy(),
                        usesEntrance(request.end(), requestedEnd), mode.getPrefix());

                // 출력은 요약만
                return summary;
            }
            else if (mode == TransportMode.CAR) {
                //티맵에서 경로찾기
                TmapCarResponse res = tmapRestClient.post()
                        .uri(
                                uriBuilder -> uriBuilder
                                        .path(mode.getPath())
                                        .queryParam("version", 1)
                                        .build())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()  //응답 받아오기
                        .body(TmapCarResponse.class);
                // 요약 부분 생성
                List<TmapCarResponse.Feature> raw = res.features();

                // 요약용 — 총거리·시간·요금은 첫 Point(S)에만 온다
                TmapCarResponse.Properties fStart = raw.stream()
                        .map(TmapCarResponse.Feature::properties)
                        .filter(p -> p.pointType() == CarPointType.S)
                        .reduce((_, _) -> { throw new BusinessException(ErrorCode.TMAP_API_ERROR); })
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_TMAP_ROUTE));

                // 여기서 한 번 정규화
                List<RouteFeature> features = raw.stream().map(RouteFeature::from).toList();
                List<Double> snappedStart = snappedStartOf(features);

                // 자동차 facilityType은 "구간 안에 그게 있음"이라 개수를 세지 않는다.
                // 고속도로 9982m가 통째로 교량으로 온 실측이 근거.
                LocationInfo start = request.start();
                LocationInfo end = request.end();
                CarSummaryResponse summary = new CarSummaryResponse(
                        0,
                        request.mode(),
                        fStart.totalDistance(), fStart.totalTime(),
                        fStart.totalFare(), fStart.taxiFare(),
                        (start.nickname() == null || start.nickname().isBlank())? start.name(): start.nickname(),
                        start.address(), List.of(start.latitude(), start.longitude()),
                        (end.nickname() == null || end.nickname().isBlank())? end.name(): end.nickname(),
                        end.address(),List.of(end.latitude(), end.longitude())
                );

                // RoutStep 생성
                List<RouteStep> steps = toSteps(features, mode);

                //경로 redis 저장
                NavigationRouteReport report = new NavigationRouteReport(
                        summary, startOrigin.source(), startOrigin.accuracy(), startOrigin.recordedAt(),
                        requestedStart, snappedStart, snapDistanceM(requestedStart, snappedStart),
                        requestedEnd, steps);
                verifyRoutePath(report, mode);
                String json = objectMapper.writeValueAsString(report);
                navigationRepository.saveRoute(currentUser.getId(), mode.getPrefix(), json);
                log.debug("Navigation search done: mode={} totalDistance={} totalTime={} steps={} pathPoints={} snapDistanceM={} startSource={} startAccuracy={} entrance={} redisPrefix={}",
                        mode, fStart.totalDistance(), fStart.totalTime(), steps.size(), report.routePath().size(),
                        report.snapDistanceM(), startOrigin.source(), startOrigin.accuracy(),
                        usesEntrance(request.end(), requestedEnd), mode.getPrefix());

                // 출력은 요약만
                return summary;
            }
            return null;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            //스택을 여기서 안 남기면 TMap 실패의 원인이 어디에도 안 남는다
            log.warn("Navigation search 실패: mode={} type={} message={}", mode, e.getClass().getName(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.TMAP_API_ERROR, e.getMessage());
        }
    }

    //대중교통 경로 검색
    public List<NavigationSummary> searchNavigationTransit(NavigationPreRequest request, CurrentUser currentUser) {
        TransportMode mode = request.mode();
        log.debug("Navigation transit search requested: userId={} role={} start=({},{}) end=({},{})",
                currentUser.getId(), currentUser.getRole(),
                request.start().latitude(), request.start().longitude(),
                request.end().latitude(), request.end().longitude());
        TmapTransitRequest req = TmapTransitRequest.from(request);
        try {
            TmapTransitResponse res = tmapRestClient.post()
                    .uri(uriBuilder -> uriBuilder.path(mode.getPath()).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    // 오류 23은 400, 한도 초과는 429로 온다. 원인은 본문에만 있으므로
                    // 기본 예외 처리를 끄고 아래 toTransitException에서 가른다
                    .onStatus(HttpStatusCode::isError, (rq, rs) -> { })
                    .body(TmapTransitResponse.class);

            // 정렬·필터까지 끝난 후보. index는 이 순서로 매긴다
            List<TmapTransitResponse.Itinerary> itineraries = usableItineraries(res);

            List<NavigationSummary> summaries = new ArrayList<>();
            List<TransitRoute> candidates = new ArrayList<>();
            for (TmapTransitResponse.Itinerary itinerary : itineraries) {
                List<TmapTransitResponse.Leg> legs = usableLegs(itinerary);
                TransitSummaryResponse summary =
                        toTransitSummary(request, itinerary, legs, summaries.size());
                summaries.add(summary);
                candidates.add(new TransitRoute(summary, toTransitLegs(legs)));
            }

            // 후보 전체를 배열로 저장한다. 선택 API는 2단계(안내 세션)에서 붙는다
            navigationRepository.saveRoute(currentUser.getId(), mode.getPrefix(),
                    objectMapper.writeValueAsString(candidates));

            log.debug("Navigation transit search done: candidates={} redisPrefix={}", summaries.size(), mode.getPrefix());
            return summaries;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Navigation transit search 실패: type={} message={}", e.getClass().getName(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.TMAP_API_ERROR, e.getMessage());
        }
    }

    //경로 선택
    @Transactional
    public void saveRoute(RouteRequest request, CurrentUser currentUser) {
        TransportMode mode = request.mode();
        log.debug("Route save requested: userId={} mode={} index={}", currentUser.getId(), mode, request.index());
        User ward = userService.currentUserToUser(currentUser.getId());
        Optional<Route> route = routeRepository.findByWardIdAndStatus(currentUser.getId(), RouteStatus.IN_PROGRESS);
        route.ifPresent(Route::canceled);
        // 취소를 먼저 내보낸다. Hibernate는 flush에서 INSERT를 UPDATE보다 앞세우므로
        // 그냥 두면 새 경로 INSERT가 아직 IN_PROGRESS인 기존 행과 유니크 인덱스에서 부딪힌다(V2_7).
        // 재탐색은 이 경로를 반복해서 타므로 상시 재현된다
        routeRepository.flush();
        String json = navigationRepository.getRoute(currentUser.getId(), mode.getPrefix()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
        if (mode.equals(TransportMode.WALK) || mode.equals(TransportMode.CAR)) {
            NavigationRouteReport report = objectMapper.readValue(json, NavigationRouteReport.class);
            Route newRoute = new Route(mode, report.summary(), json,ward);
            routeRepository.save(newRoute);
            log.debug("Route saved: routeId={} wardId={} mode={}", newRoute.getId(), ward.getId(), mode);
        } else {
            //대중교통일때
            TransitRoute report = Arrays.stream(objectMapper.readValue(json, TransitRoute[].class))
                    .filter(candidate -> Objects.equals(candidate.summary().index(), request.index()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
            Route newRoute = new Route(mode, report.summary(), objectMapper.writeValueAsString(report), ward);
            routeRepository.save(newRoute);
            log.debug("Route saved: routeId={} wardId={} mode={} index={}", newRoute.getId(), ward.getId(), mode, request.index());
        }
    }

    @Transactional(readOnly = true)
    public NavigationResponse getProcessingRoute(CurrentUser currentUser) {
        Long wardId;
        if (currentUser.getRole() == UserRole.WARD) wardId = currentUser.getId();
        else wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        Route route = routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
        Integer remaining = remainingDistanceOf(route, wardId);
        log.debug("Route processing read: wardId={} routeId={} mode={} remainingDistanceM={}",
                wardId, route.getId(), route.getMode(), remaining);
        return NavigationResponse.from(route, remaining);
    }

    /**
     * 남은 거리를 구하려고 저장된 경로 JSON을 한 번 더 파싱한다.
     * <p>
     * `/processing`은 `report`를 `@JsonRawValue`로 그대로 흘려보내서 원래는 파싱이 필요 없었다.
     * 안내 중에 반복 호출되는 API라 부담이 되면 계산 결과를 Redis에 짧게 캐시하는 쪽을 봐야 한다.
     */
    private Integer remainingDistanceOf(Route route, Long wardId) {
        LocationReport location = usableLastLocationOf(wardId);
        if (location == null) return null;

        String json = route.getReport();
        TransportMode mode = route.getMode();
        if (mode.equals(TransportMode.WALK) || mode.equals(TransportMode.CAR)) {
            return remainingDistance(objectMapper.readValue(json, NavigationRouteReport.class), location);
        }
        return remainingDistance(objectMapper.readValue(json, TransitRoute.class), location);
    }

    private Integer remainingDistance(NavigationRouteReport report, LocationReport location) {
        if (location == null) return null;
        return routeProgressCalculator.remainingDistance(
                report, location.latitude(), location.longitude(), report.summary().totalDistance());
    }

    private Integer remainingDistance(TransitRoute report, LocationReport location) {
        if (location == null) return null;
        return routeProgressCalculator.remainingDistance(
                report, location.latitude(), location.longitude(), report.summary().totalDistance());
    }


    @Transactional
    public void completeRoute(CurrentUser currentUser) {
        Route route = routeRepository.findByWardIdAndStatus(currentUser.getId(), RouteStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
        route.completed();
        log.debug("Route completed: wardId={} routeId={}", currentUser.getId(), route.getId());
    }


    @Transactional
    public void cancelRoute(CurrentUser currentUser) {
        Route route = routeRepository.findByWardIdAndStatus(currentUser.getId(), RouteStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
        route.canceled();
        log.debug("Route canceled: wardId={} routeId={}", currentUser.getId(), route.getId());
    }


    @Transactional(readOnly = true)
    public MapResponse getMapRoute(CurrentUser currentUser) {
        Long wardId = currentUser.getRole() == UserRole.WARD
                ? currentUser.getId()
                : userService.getWardIdFromGuardianId(currentUser.getId());
        Optional<Route> opRoute = routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS);
        if (opRoute.isPresent()) {
            Route route = opRoute.get();
            TransportMode mode = route.getMode();
            String json = route.getReport();
            LocationReport location = usableLastLocationOf(wardId);
            log.debug("Map route read: wardId={} routeId={} mode={} hasLocation={}",
                    wardId, route.getId(), mode, location != null);
            if (mode.equals(TransportMode.WALK) || mode.equals(TransportMode.CAR)) {
                NavigationRouteReport report = objectMapper.readValue(json, NavigationRouteReport.class);
                return MapResponse.from(report, mode, route.getCreatedAt(), remainingDistance(report, location));
            } else {
                //대중교통일때
                TransitRoute report = objectMapper.readValue(json, TransitRoute.class);
                return MapResponse.from(report, route.getCreatedAt(), remainingDistance(report, location));
            }
        }
        else {
            //200 + data=null 로 나간다. 앱에서는 '진행 중 경로 없음'과 '서버 오류'가 구분되지 않는다
            log.debug("Map route read: 진행 중 경로 없음 — callerId={} wardId={}", currentUser.getId(), wardId);
            return null;
        }
    }

    /**
     * 남은 거리 계산용 현재 좌표. 없으면 null이고 남은 거리도 null이 된다.
     * <p>
     * `LocationService.getLastLocation`을 쓰지 않는 이유는 그쪽이 좌표마다 티맵 역지오코딩을 부르기 때문이다.
     * 여기서는 주소가 필요 없고, 지도 갱신 주기마다 외부 API를 때릴 이유가 없다.
     */
    private LocationReport lastLocationOf(Long wardId) {
        return realtimeLocationRepository.getLastLocation(wardId)
                .map(json -> objectMapper.readValue(json, LocationReport.class))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RouteSummary> getRoutesInWeek(CurrentUser currentUser) {
        // 오늘 포함 7일치 → 6일 전 KST 00:00부터. alert·command와 같은 규칙이다.
        // createdAt은 DateTimeProvider가 KST 벽시계로 채우므로 그대로 비교할 수 있다
        LocalDateTime from = LocalDate.now(SEOUL).minusDays(6).atStartOfDay();
        Long wardId = currentUser.getRole()==UserRole.WARD? currentUser.getId() : userService.getWardIdFromGuardianId(currentUser.getId());
        List<RouteSummary> routes = routeRepository.findAllByWardIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(wardId, from);
        log.debug("Route week list: wardId={} from={} count={}", wardId, from, routes.size());
        return routes;
    }


    private @NonNull MultiValueMap<String, String> getStringStringMultiValueMap(NavigationPreRequest request, TransportMode mode,
                                                                               List<Double> start, List<Double> end) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("startX", String.valueOf(start.getLast()));    // X가 경도
        form.add("startY", String.valueOf(start.getFirst()));
        form.add("endX",   String.valueOf(end.getLast()));
        form.add("endY",   String.valueOf(end.getFirst()));
        form.add("startName", request.start().name());                      // 원문 그대로
        form.add("endName",   request.end().name());
        form.add("searchOption", mode.getOption());  //대중교통이 아닐 경우에만 추가
        form.add("reqCoordType", "WGS84GEO");
        form.add("resCoordType", "WGS84GEO");
        return form;
    }

    /**
     * 티맵에 보낼 출발 좌표와 그 출처.
     *
     * <p><b>요청 좌표 → 저장 좌표 → 실패</b> 순이다. 순서를 고정한 이유가 있다 — 서버가 「더 정확해
     * 보이는 쪽」을 임의로 고르면, 클라이언트가 월드를 정렬한 기준 좌표와 안내 시작점이 어긋난다.
     * 요청 좌표가 문턱을 넘으면 저장 좌표가 더 좋아 보여도 요청 좌표를 쓴다.
     *
     * <p>둘 다 못 넘으면 경로를 만들지 않고 {@link ErrorCode#LOW_CONFIDENCE_LOCATION}을 던진다.
     * 나쁜 좌표로 만든 경로는 음성 안내와 화면이 어긋나므로, 틀린 경로보다 「위치 확인 중」이 낫다.
     * 2026-08-26 실측에서 accuracy 100m짜리 재전송 좌표로 경로가 만들어져 실제 위치에서
     * 218m 떨어진 곳에서 안내가 시작됐다.
     *
     * <p>저장 좌표의 수명을 이동 상태로 나눈 이유 — 고정값으로는 못 맞춘다. 90초는 보행 중
     * 90m 오차이고, 30초는 정지 중 상시 만료다(실측 보고 간격 58~62초). 판정이 틀릴 때는
     * 정지 확정이 늦는 쪽이라 오차 방향이 안전하다.
     */
    private StartOrigin resolveStart(NavigationPreRequest request, Long wardId) {
        Instant now = Instant.now();

        Long age = ageSeconds(request.startRecordedAt(), now);
        if (trustworthy(request.startAccuracy(), age, REQUEST_MAX_AGE_SEC)) {
            return new StartOrigin(List.of(request.start().latitude(), request.start().longitude()),
                    StartSource.REQUEST, request.startAccuracy(), request.startRecordedAt());
        }
        log.info("출발 좌표 — 요청값을 쓰지 않는다: wardId={} accuracy={} ageSec={}",
                wardId, request.startAccuracy(), age);

        LocationReport last = lastLocationSafely(wardId);
        if (last != null && last.latitude() != null && last.longitude() != null) {
            Long lastAge = ageSeconds(last.recordedAt(), now);
            long limit = last.status() == MovementStatus.STATIONARY
                    ? CACHE_MAX_AGE_STATIONARY_SEC : CACHE_MAX_AGE_MOVING_SEC;
            if (trustworthy(last.accuracy(), lastAge, limit)) {
                log.info("출발 좌표 — 저장 위치로 폴백: wardId={} accuracy={} ageSec={} status={}",
                        wardId, last.accuracy(), lastAge, last.status());
                return new StartOrigin(List.of(last.latitude(), last.longitude()),
                        StartSource.SERVER_CACHE, last.accuracy(), last.recordedAt());
            }
            log.info("출발 좌표 — 저장 위치도 못 쓴다: wardId={} accuracy={} ageSec={} status={} limitSec={}",
                    wardId, last.accuracy(), lastAge, last.status(), limit);
        }
        throw new BusinessException(ErrorCode.LOW_CONFIDENCE_LOCATION);
    }

    /**
     * 좌표를 경로 출발점으로 쓸 수 있는지. 정확도와 나이를 모두 넘겨야 한다.
     *
     * <p>{@code accuracy}가 없거나 0 이하이면 「오차 0」이 아니라 「정확도 없음」이므로 불신한다.
     * 나이를 모르면({@code age == null}) 측정 시각이 없거나 시계가 5초 넘게 미래인 경우다.
     */
    private boolean trustworthy(Float accuracy, Long ageSec, long limitSec) {
        if (accuracy == null || accuracy <= 0f || accuracy > START_ACCURACY_MAX_M) return false;
        return ageSec != null && ageSec <= limitSec;
    }

    /**
     * 측정 시각부터 지금까지의 초. 못 믿을 값이면 {@code null}.
     *
     * <p>폰 시계가 서버보다 앞선 보고가 실측된다(2026-08-26 관측 +1.1초). 그 정도는 시계 오차로
     * 보고 0초로 클램프하되, {@link #CLOCK_SKEW_TOLERANCE_SEC}를 넘는 미래 시각은 불신한다.
     * 무제한으로 클램프하면 시계가 크게 틀어진 단말의 좌표가 영원히 「방금 측정됨」이 된다.
     */
    private Long ageSeconds(Instant recordedAt, Instant now) {
        if (recordedAt == null) return null;
        long age = Duration.between(recordedAt, now).getSeconds();
        if (age >= 0) return age;
        return -age <= CLOCK_SKEW_TOLERANCE_SEC ? 0L : null;
    }

    /**
     * 남은 거리 계산에 쓸 수 있는 최신 위치. 못 믿을 좌표면 {@code null}이고 남은 거리도 null이 된다.
     * <p>
     * 경로 출발점과 같은 문턱을 태운다. 정확도 100m짜리 재전송 좌표로 남은 거리를 재면
     * 가만히 서 있어도 수십 m씩 튄다.
     */
    private LocationReport usableLastLocationOf(Long wardId) {
        LocationReport last = lastLocationOf(wardId);
        if (last == null) return null;
        Long age = ageSeconds(last.recordedAt(), Instant.now());
        long limit = last.status() == MovementStatus.STATIONARY
                ? CACHE_MAX_AGE_STATIONARY_SEC : CACHE_MAX_AGE_MOVING_SEC;
        if (trustworthy(last.accuracy(), age, limit)) return last;
        log.debug("남은 거리 계산 생략 — wardId={} accuracy={} ageSec={} status={}",
                wardId, last.accuracy(), age, last.status());
        return null;
    }

    /** 피보호자 id. 보호자가 호출하면 자기 id가 아니라 피보호자 id로 최신 위치를 찾아야 한다 */
    private Long wardIdOf(CurrentUser currentUser) {
        return currentUser.getRole() == UserRole.WARD
                ? currentUser.getId()
                : userService.getWardIdFromGuardianId(currentUser.getId());
    }

    /** 최신 위치 읽기 실패가 길안내를 막지 않게 한다. 폴백 대상일 뿐이라 502로 올릴 이유가 없다 */
    private LocationReport lastLocationSafely(Long wardId) {
        try {
            return lastLocationOf(wardId);
        } catch (Exception e) {
            log.warn("최신 위치를 읽지 못했다: wardId={} message={}", wardId, e.getMessage());
            return null;
        }
    }

    /** 목적지로 중심점이 아니라 보행자 입구점을 썼는지. 로그에만 쓴다 */
    private boolean usesEntrance(LocationInfo end, List<Double> requestedEnd) {
        return !requestedEnd.equals(List.of(end.latitude(), end.longitude()));
    }

    /**
     * 티맵이 보정해 돌려준 출발 좌표. 첫 Point가 출발 안내점이다
     * (실측 — `SP`/`S`는 정확히 1개이고 features 맨 앞에 온다).
     */
    private List<Double> snappedStartOf(List<RouteFeature> features) {
        return features.stream()
                .filter(f -> f.type() == GeometryType.POINT)
                .findFirst()
                .map(f -> toLatLng(f.coordinates()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_TMAP_ROUTE));
    }

    /** 요청 좌표와 티맵 보정 좌표 사이 거리(m). GPS 오차 수준이라 소수 첫째 자리까지만 의미가 있다 */
    private Double snapDistanceM(List<Double> requested, List<Double> snapped) {
        if (requested == null || snapped == null) return null;
        double distance = GeoUtils.distanceMeters(
                requested.getFirst(), requested.getLast(), snapped.getFirst(), snapped.getLast());
        return Math.round(distance * 10) / 10.0;
    }

    /**
     * 폴리라인이 실제로 만들어졌는지 확인한다. 좌표가 2개 미만이면 클라이언트가 경로 형상을
     * 그릴 수 없으므로 빈 배열을 응답하지 않고 여기서 끊는다.
     *
     * 중간 step 하나가 비는 것은 502로 올리지 않는다 — 티맵이 LineString 없이 Point를 연달아
     * 주는 경우가 있는지 실측이 없어서, 경로 전체를 버리는 쪽이 더 위험하다. 로그만 남긴다.
     * 마지막 step(EP)은 뒤 구간이 없어 비는 것이 정상이다.
     */
    private void verifyRoutePath(NavigationRouteReport report, TransportMode mode) {
        List<RouteStep> steps = report.report();
        if (report.routePath().size() < 2) {
            log.warn("Navigation 경로 폴리라인이 비어 있다: mode={} steps={}", mode, steps.size());
            throw new BusinessException(ErrorCode.TMAP_API_ERROR);
        }
        List<Integer> empty = steps.subList(0, steps.size() - 1).stream()
                .filter(step -> step.pathToNext() == null || step.pathToNext().isEmpty())
                .map(RouteStep::sequence)
                .toList();
        if (!empty.isEmpty()) {
            log.warn("Navigation step의 pathToNext가 비어 있다: mode={} sequences={}", mode, empty);
        }
    }

    //횡단보도 세기
    private int countGroups(List<RouteFeature> lines, FacilityType type) {
        int count = 0;
        boolean inGroup = false;
        for (RouteFeature f : lines) {
            boolean match = (type == f.facility());
            if (match && !inGroup) count++;   //새구간의 시작
            inGroup = match;
        }
        return count;
    }

    // Tmap coordinates는 [경도, 위도] 순서. 우리는 [위도, 경도]로 뒤집어 담는다.
    private List<Double> toLatLng(JsonNode coordinate) {
        return List.of(coordinate.get(1).doubleValue(),   // 위도
                coordinate.get(0).doubleValue());  // 경도
    }

    // LineString 좌표를 path 뒤에 이어붙인다. 두 번째 구간부터는 첫 좌표가 겹치므로 건너뛴다.
    private void appendPath(List<List<Double>> path, JsonNode coordinates) {
        int from = path.isEmpty() ? 0 : 1;
        for (int i = from; i < coordinates.size(); i++) {
            path.add(toLatLng(coordinates.get(i)));
        }
    }

    private List<RouteStep> toSteps(List<RouteFeature> features, TransportMode mode) {
        FacilityType defaultFacility = (mode == TransportMode.WALK)
                ? FacilityType.WALKWAY : FacilityType.NORMAL;
        boolean splitOnFacility = (mode == TransportMode.WALK);  // 자동차는 구간 전체에 뭉개져 온다

        List<RouteStep> steps = new ArrayList<>();
        Box box = null;
        int cumulative = 0;

        for (RouteFeature f : features) {
            if (f.type() == GeometryType.POINT) {
                if (box != null) cumulative = flush(steps, box, cumulative, splitOnFacility);
                List<Double> latLng = toLatLng(f.coordinates());
                box = new Box(defaultFacility);
                box.lat = latLng.get(0);
                box.lng = latLng.get(1);
                box.description = f.description();
                box.turnType = f.turnType();
                box.pointType = f.pointType();
                continue;
            }

            FacilityType lineFacility = f.facility() == null ? defaultFacility : f.facility();

            // 시설이 바뀌면 여기서 상자를 닫고, 이 구간의 첫 좌표에서 새로 연다
            if (splitOnFacility && box != null && !box.path.isEmpty() && lineFacility != box.facility) {
                List<Double> here = toLatLng(f.coordinates().get(0));
                cumulative = flush(steps, box, cumulative, splitOnFacility);
                box = new Box(defaultFacility);
                box.lat = here.get(0);
                box.lng = here.get(1);
                box.pointType = RouteStepType.FP.getDescription();
                box.description = lineFacility.getMessage();
            }
            if (box == null) continue;

            box.facility = lineFacility;
            box.distance += f.distance();
            box.time     += f.time();
            appendPath(box.path, f.coordinates());
        }

        if (box != null) flush(steps, box, cumulative, splitOnFacility);
        return steps;
    }

    //step 생성을 위한 임시 박스
    private static final class Box {
        Double lat, lng;
        String description;
        Integer turnType;
        String pointType;
        FacilityType facility;   // 초기값은 생성자에서 모드별로 받는다
        int distance = 0;
        int time = 0;
        List<List<Double>> path = new ArrayList<>();

        Box(FacilityType defaultFacility) {
            this.facility = defaultFacility;
        }
    }

    // withFacility=false면 facility를 안 싣는다. 자동차는 facilityType이 구간 전체에 뭉개져 와서
    // "경부고속도로 14348m = 교량"처럼 거짓이 된다. 그 정보는 turnType과 description에 이미 정확히 있다.
    private int flush(List<RouteStep> steps, Box box, int cumulative, boolean withFacility) {
        boolean empty = box.path.isEmpty();          // EP는 뒤 구간이 없다
        steps.add(new RouteStep(
                steps.size(), box.lat, box.lng,
                withRealDistance(box.description, box.distance),
                box.turnType, box.pointType,
                (empty || !withFacility || box.facility == null) ? null : box.facility.getLabel(),
                empty ? null : box.distance,
                empty ? null : box.time,
                cumulative, box.path));
        return cumulative + box.distance;
    }

    private String withRealDistance(String description, int distance) {
        if (description == null) return null;
        Matcher m = DISTANCE_TAIL.matcher(description);
        return m.find() ? m.replaceFirst(distance + "m 이동") : description;
    }

    /* ==================== 대중교통 ==================== */

    private static final String WALK_MODE = "WALK";

    /** 성공/실패를 metaData 유무로 먼저 가르고, 탈 수 있는 후보만 정렬해서 돌려준다. */
    private List<TmapTransitResponse.Itinerary> usableItineraries(TmapTransitResponse res) {
        if (res == null) throw new BusinessException(ErrorCode.TMAP_API_ERROR);
        if (res.metaData() == null || res.metaData().plan() == null) throw toTransitException(res);

        List<TmapTransitResponse.Itinerary> found = res.metaData().plan().itineraries();
        if (found == null || found.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND_TMAP_ROUTE);

        // 티맵은 정렬을 안 해준다. 시각장애인 기준으로 환승과 도보가 가장 큰 비용이다
        List<TmapTransitResponse.Itinerary> usable = found.stream()
                .filter(this::inService)
                .sorted(Comparator
                        .comparingInt((TmapTransitResponse.Itinerary i) -> orMax(i.transferCount()))
                        .thenComparingInt(i -> orMax(i.totalWalkDistance())))
                .toList();

        if (usable.isEmpty()) throw new BusinessException(ErrorCode.NOT_IN_SERVICE);
        return usable;
    }

    private int orMax(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    /**
     * 오류가 HTTP 200으로도 오고 400·429로도 오므로 상태코드가 아니라 본문으로 가른다.
     * result는 API 층, error는 게이트웨이 층이고 둘이 같이 오지 않는다.
     */
    private BusinessException toTransitException(TmapTransitResponse res) {
        if (res.result() != null && res.result().status() != null) {
            String message = res.result().message();
            return switch (res.result().status()) {
                // 11 가까움 · 12 출발 정류장 없음 · 13 도착 정류장 없음 · 14 기타
                case 11, 12, 13, 14 -> new BusinessException(ErrorCode.NOT_FOUND_TMAP_ROUTE);
                // 21 형식 · 22 누락 · 23 서비스 지역 아님 · 24 타임머신 시각
                case 21, 22, 23, 24 -> new BusinessException(ErrorCode.INVALID_LOCATION, message);
                default -> new BusinessException(ErrorCode.TMAP_API_ERROR, message);
            };
        }
        // 인증 실패·한도 초과(QUOTA_EXCEEDED)가 여기로 온다
        if (res.error() != null) return new BusinessException(ErrorCode.TMAP_API_ERROR, res.error().message());
        return new BusinessException(ErrorCode.TMAP_API_ERROR);
    }

    /**
     * service 0이 하나라도 섞이면 못 타는 경로다. searchDttm은 경로 탐색을 안 바꾸고
     * 이 플래그만 바꾸므로 운행 종료 노선이 결과에 그대로 남는다. 도보 leg엔 service 키가 없다.
     */
    private boolean inService(TmapTransitResponse.Itinerary itinerary) {
        if (itinerary.legs() == null) return false;
        return itinerary.legs().stream()
                .map(TmapTransitResponse.Leg::service)
                .filter(Objects::nonNull)
                .allMatch(service -> service == 1);
    }

    /** 같은 정류장에서 갈아탈 때 거리 0짜리 도보 leg가 온다. 그대로 두면 "0m 이동하세요"가 된다. */
    private List<TmapTransitResponse.Leg> usableLegs(TmapTransitResponse.Itinerary itinerary) {
        return itinerary.legs().stream()
                .filter(leg -> !(WALK_MODE.equals(leg.mode())
                        && (leg.distance() == null || leg.distance() == 0)))
                .toList();
    }

    private TransitSummaryResponse toTransitSummary(NavigationPreRequest request,
                                                    TmapTransitResponse.Itinerary itinerary,
                                                    List<TmapTransitResponse.Leg> legs,
                                                    int index) {
        LocationInfo start = request.start();
        LocationInfo end = request.end();
        Integer totalFare = (itinerary.fare() == null || itinerary.fare().regular() == null)
                ? null : itinerary.fare().regular().totalFare();

        // 총계는 itinerary 값을 그대로 쓴다. leg distance 합과 안 맞으므로 합산하지 않는다
        return new TransitSummaryResponse(
                index,
                request.mode(),
                itinerary.totalDistance(), itinerary.totalTime(),
                itinerary.totalWalkTime(), itinerary.totalWalkDistance(),
                itinerary.transferCount(), totalFare,
                legs.stream().map(this::toLegSummary).toList(),
                (start.nickname() == null || start.nickname().isBlank()) ? start.name() : start.nickname(),
                start.address(), List.of(start.latitude(), start.longitude()),
                (end.nickname() == null || end.nickname().isBlank()) ? end.name() : end.nickname(),
                end.address(), List.of(end.latitude(), end.longitude())
        );
    }

    private TransitSummaryResponse.LegSummary toLegSummary(TmapTransitResponse.Leg leg) {
        List<TmapTransitResponse.Station> stations = stationsOf(leg);
        return new TransitSummaryResponse.LegSummary(
                modeLabel(leg.mode()),
                leg.route(),
                routesOf(leg),
                leg.start() == null ? null : leg.start().name(),
                leg.end() == null ? null : leg.end().name(),
                stations.isEmpty() ? null : stations.size() - 1,   // 승차 정류장을 포함해서 온다
                leg.sectionTime(),
                leg.distance());
    }

    /** 최종 출력이 TTS라 라벨로 내보낸다. 원문은 report에 그대로 남는다. */
    private String modeLabel(String mode) {
        if (mode == null) return null;
        return switch (mode) {
            case WALK_MODE -> "도보";
            case "BUS" -> "버스";
            case "SUBWAY" -> "지하철";
            case "EXPRESSBUS" -> "고속/시외버스";
            case "TRAIN" -> "기차";
            case "AIRPLANE" -> "항공";
            case "FERRY" -> "해운";
            default -> mode;
        };
    }

    /** 탈 수 있는 노선 = 대표 노선 + Lane 전부. Lane에 대표 노선은 안 들어온다. */
    private List<String> routesOf(TmapTransitResponse.Leg leg) {
        if (leg.route() == null) return List.of();
        List<String> routes = new ArrayList<>();
        routes.add(leg.route());
        if (leg.lane() == null) return routes;
        leg.lane().stream()
                .filter(lane -> lane.service() != null && lane.service() == 1)
                .map(TmapTransitResponse.Lane::route)
                .filter(route -> route != null && !routes.contains(route))
                .forEach(routes::add);
        return routes;
    }

    private List<TmapTransitResponse.Station> stationsOf(TmapTransitResponse.Leg leg) {
        if (leg.passStopList() == null || leg.passStopList().stations() == null) return List.of();
        return leg.passStopList().stations();
    }

    private List<TransitRoute.TransitLeg> toTransitLegs(List<TmapTransitResponse.Leg> legs) {
        List<TransitRoute.TransitLeg> result = new ArrayList<>();
        for (TmapTransitResponse.Leg leg : legs) {
            result.add(new TransitRoute.TransitLeg(
                    result.size(),
                    leg.mode(), leg.route(), leg.routeId(), leg.type(), leg.routeColor(),
                    routesOf(leg),
                    leg.sectionTime(), leg.distance(), leg.routePayment(),
                    leg.start() == null ? null : leg.start().name(),
                    leg.start() == null ? null : toCoordinate(leg.start().lat(), leg.start().lon()),
                    leg.end() == null ? null : leg.end().name(),
                    leg.end() == null ? null : toCoordinate(leg.end().lat(), leg.end().lon()),
                    toTransitStations(leg),
                    toTransitSteps(leg),
                    leg.passShape() == null ? List.of() : toTransitPath(leg.passShape().linestring())));
        }
        return result;
    }

    private List<TransitRoute.TransitStation> toTransitStations(TmapTransitResponse.Leg leg) {
        return stationsOf(leg).stream()
                .map(station -> new TransitRoute.TransitStation(
                        station.index(), station.stationID(), station.stationName(),
                        // 정류장 좌표는 문자열로 온다. leg.start/end의 숫자와 값도 미세하게 다르다
                        toCoordinate(toDouble(station.lat()), toDouble(station.lon()))))
                .toList();
    }

    /** 환승 도보엔 steps가 아예 없다. 예외가 아니라 규칙이다 — 그 구간은 안내문이 없다. */
    private List<TransitRoute.TransitStep> toTransitSteps(TmapTransitResponse.Leg leg) {
        if (leg.steps() == null) return List.of();
        List<TransitRoute.TransitStep> steps = new ArrayList<>();
        for (TmapTransitResponse.Step step : leg.steps()) {
            steps.add(new TransitRoute.TransitStep(
                    steps.size(), step.description(), step.distance(), step.streetName(),
                    toTransitPath(step.linestring())));
        }
        return steps;
    }

    /** "경도,위도 경도,위도 …" 공백 구분 문자열을 [위도, 경도] 목록으로 뒤집는다. */
    private List<List<Double>> toTransitPath(String linestring) {
        if (linestring == null || linestring.isBlank()) return List.of();
        List<List<Double>> path = new ArrayList<>();
        for (String pair : linestring.trim().split("\\s+")) {
            String[] lonLat = pair.split(",");
            if (lonLat.length != 2) continue;   // 좌표 사이 공백이 빠진 조각을 버린다
            List<Double> coordinate = toCoordinate(toDouble(lonLat[1]), toDouble(lonLat[0]));
            if (coordinate != null) path.add(coordinate);
        }
        return path;
    }

    private List<Double> toCoordinate(Double latitude, Double longitude) {
        return (latitude == null || longitude == null) ? null : List.of(latitude, longitude);
    }

    private Double toDouble(String value) {
        try {
            return value == null ? null : Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
