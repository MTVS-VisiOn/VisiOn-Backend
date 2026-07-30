package mtvs.onvision.vision.navigation.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NavigationService {
    private final RestClient tmapRestClient;
    private final NavigationRepository navigationRepository;
    private final ObjectMapper objectMapper;
    private final RouteRepository routeRepository;

    private static final Pattern DISTANCE_TAIL = Pattern.compile("\\d+m 이동$");
    private final UserService userService;


    //네비게이션 경로 찾기
    public NavigationSummary searchNavigation(NavigationPreRequest request, CurrentUser currentUser) {
        TransportMode mode = request.mode();
        MultiValueMap<String, String> form = getStringStringMultiValueMap(request, mode);
        try {
            if (!(mode == TransportMode.WALK) && !(mode == TransportMode.CAR)) throw new BusinessException(ErrorCode.INVALID_TRANSFER);
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
                        start.roadAddress(), List.of(start.latitude(), start.longitude()),
                        (end.nickname() == null || end.nickname().isBlank())? end.name(): end.nickname(),
                        end.roadAddress(),List.of(end.latitude(), end.longitude())
                );

                // RoutStep 생성
                List<RouteStep> steps = toSteps(features, mode);

                //경로 redis 저장
                NavigationRouteReport report = new NavigationRouteReport(summary, steps);
                String json = objectMapper.writeValueAsString(report);
                navigationRepository.saveRoute(currentUser.getId(), mode.getPrefix(), json);

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
                        start.roadAddress(), List.of(start.latitude(), start.longitude()),
                        (end.nickname() == null || end.nickname().isBlank())? end.name(): end.nickname(),
                        end.roadAddress(),List.of(end.latitude(), end.longitude())
                );

                // RoutStep 생성
                List<RouteStep> steps = toSteps(features, mode);

                //경로 redis 저장
                NavigationRouteReport report = new NavigationRouteReport(summary, steps);
                String json = objectMapper.writeValueAsString(report);
                navigationRepository.saveRoute(currentUser.getId(), mode.getPrefix(), json);

                // 출력은 요약만
                return summary;
            }
            return null;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TMAP_API_ERROR, e.getMessage());
        }
    }

    //대중교통 경로 검색
    public List<NavigationSummary> searchNavigationTransit(NavigationPreRequest request, CurrentUser currentUser) {
        TransportMode mode = request.mode();
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

            return summaries;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TMAP_API_ERROR, e.getMessage());
        }
    }

    //경로 선택
    @Transactional
    public void saveRoute(@Valid RouteRequest request, CurrentUser currentUser) {
        TransportMode mode = request.mode();
        User ward = userService.currentUserToUser(currentUser.getId());
        Optional<Route> route = routeRepository.findByWardIdAndStatus(currentUser.getId(), RouteStatus.IN_PROGRESS);
        route.ifPresent(Route::canceled);
        String json = navigationRepository.getRoute(currentUser.getId(), mode.getPrefix()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
        if (mode.equals(TransportMode.WALK) || mode.equals(TransportMode.CAR)) {
            NavigationRouteReport report = objectMapper.readValue(json, NavigationRouteReport.class);
            Route newRoute = new Route(report, json,ward);
            routeRepository.save(newRoute);
        } else {
            //대중교통일때
            TransitRoute report = Arrays.stream(objectMapper.readValue(json, TransitRoute[].class))
                    .filter(candidate -> Objects.equals(candidate.summary().index(), request.index()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
            String reportjson = objectMapper.writeValueAsString(report);
            Route newRoute = new Route(report, reportjson, ward);
            routeRepository.save(newRoute);
        }
    }

    @Transactional(readOnly = true)
    public NavigationResponse getProcessingRoute(CurrentUser currentUser) {
        Long wardId;
        if (currentUser.getRole() == UserRole.WARD) wardId = currentUser.getId();
        else wardId = userService.getWardIdFromGuardianId(currentUser.getId());
        Route route = routeRepository.findByWardIdAndStatus(wardId, RouteStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
        return NavigationResponse.from(route);
    }



    private @NonNull MultiValueMap<String, String> getStringStringMultiValueMap(NavigationPreRequest request, TransportMode mode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("startX", String.valueOf(request.start().longitude()));   // X가 경도
        form.add("startY", String.valueOf(request.start().latitude()));
        form.add("endX",   String.valueOf(request.end().longitude()));
        form.add("endY",   String.valueOf(request.end().latitude()));
        form.add("startName", request.start().name());                      // 원문 그대로
        form.add("endName",   request.end().name());
        form.add("searchOption", mode.getOption());  //대중교통이 아닐 경우에만 추가
        form.add("reqCoordType", "WGS84GEO");
        form.add("resCoordType", "WGS84GEO");
        return form;
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
                start.roadAddress(), List.of(start.latitude(), start.longitude()),
                (end.nickname() == null || end.nickname().isBlank()) ? end.name() : end.nickname(),
                end.roadAddress(), List.of(end.latitude(), end.longitude())
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
