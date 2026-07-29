package mtvs.onvision.vision.navigation.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.navigation.domain.TransportMode;
import mtvs.onvision.vision.navigation.dto.*;
import mtvs.onvision.vision.navigation.repository.NavigationRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NavigationService {
    private final RestClient tmapRestClient;
    private final NavigationRepository navigationRepository;
    private final ObjectMapper objectMapper;
    private static final Pattern DISTANCE_TAIL = Pattern.compile("\\d+m 이동$");

    //네비게이션 경로 찾기
    public NavigationSummary searchNavigation(NavigationPreRequest request, CurrentUser currentUser) {
        TransportMode mode = request.mode();
        MultiValueMap<String, String> form = getStringStringMultiValueMap(request, mode);
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
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));

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
                navigationRepository.saveRoute(currentUser.getId(), json);

                // 출력은 요약만
                return summary;
            }
            if (mode == TransportMode.CAR) {
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
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));

                // 여기서 한 번 정규화
                List<RouteFeature> features = raw.stream().map(RouteFeature::from).toList();

                // 자동차 facilityType은 "구간 안에 그게 있음"이라 개수를 세지 않는다.
                // 고속도로 9982m가 통째로 교량으로 온 실측이 근거.
                LocationInfo start = request.start();
                LocationInfo end = request.end();
                CarSummaryResponse summary = new CarSummaryResponse(
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
                navigationRepository.saveRoute(currentUser.getId(), json);

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

    private @NonNull MultiValueMap<String, String> getStringStringMultiValueMap(NavigationPreRequest request, TransportMode mode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("startX", String.valueOf(request.start().longitude()));   // X가 경도
        form.add("startY", String.valueOf(request.start().latitude()));
        form.add("endX",   String.valueOf(request.end().longitude()));
        form.add("endY",   String.valueOf(request.end().latitude()));
        form.add("startName", request.start().name());                      // 원문 그대로
        form.add("endName",   request.end().name());
        form.add("searchOption", mode.getOption());
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
}
