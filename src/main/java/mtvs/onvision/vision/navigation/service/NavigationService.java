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
    public NavigationSummaryResponse searchNavigation(NavigationPreRequest request, CurrentUser currentUser) {
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
                List<TmapPedestrianResponse.Feature> features = res.features();
                TmapPedestrianResponse.Properties fStart = features.stream()
                        .map(TmapPedestrianResponse.Feature::properties)
                        .filter(p -> p.pointType() == RouteStepType.SP)
                        .reduce((_, _) -> { throw new BusinessException(ErrorCode.TMAP_API_ERROR); })  //2개인 경우 에러
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ROUTE));
                Integer totalDistance = fStart.totalDistance();
                Integer totalTime = fStart.totalTime();
                List<TmapPedestrianResponse.Properties> lines = features.stream()
                        .filter(f -> f.geometry().type() == GeometryType.LINE_STRING)
                        .map(TmapPedestrianResponse.Feature::properties)
                        .toList();

                int crosswalkCount = countGroups(lines, FacilityType.CROSSWALK);
                int stairDistance = countGroups(lines, FacilityType.STAIRS);
                int overpassDistance = countGroups(lines, FacilityType.OVERPASS);
                int underpassDistance = countGroups(lines, FacilityType.UNDERPASS);
                LocationInfo start = request.start();
                LocationInfo end = request.end();
                NavigationSummaryResponse summary = new NavigationSummaryResponse(
                        totalDistance, totalTime, crosswalkCount,
                        stairDistance, overpassDistance, underpassDistance,
                        (start.nickname() == null || start.nickname().isBlank())? start.name(): start.nickname(),
                        start.roadAddress(), List.of(start.latitude(), start.longitude()),
                        (end.nickname() == null || end.nickname().isBlank())? end.name(): end.nickname(),
                        end.roadAddress(),List.of(end.latitude(), end.longitude())
                );

                // RoutStep 생성
                List<RouteStep> steps = toSteps(features);

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
        return form;
    }

    //횡단보도 세기
    private int countGroups(List<TmapPedestrianResponse.Properties> lines, FacilityType type) {
        int count = 0;
        boolean inGroup = false;
        for (TmapPedestrianResponse.Properties p : lines) {
            boolean match = type.matches(p.facilityType());
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

    private List<RouteStep> toSteps(List<TmapPedestrianResponse.Feature> features) {
        List<RouteStep> steps = new ArrayList<>();
        Box box = null;
        int cumulative = 0;

        for (TmapPedestrianResponse.Feature f : features) {
            TmapPedestrianResponse.Properties p = f.properties();

            if (f.geometry().type() == GeometryType.POINT) {
                if (box != null) cumulative = flush(steps, box, cumulative);
                List<Double> latLng = toLatLng(f.geometry().coordinates());
                box = new Box();
                box.lat = latLng.get(0);
                box.lng = latLng.get(1);
                box.description = p.description();
                box.turnType = p.turnType();
                box.pointType = p.pointType();
                continue;
            }

            FacilityType lineFacility = FacilityType.from(p.facilityType());
            if (lineFacility == null) lineFacility = FacilityType.NORMAL;

            // 시설이 바뀌면 여기서 상자를 닫고, 이 구간의 첫 좌표에서 새로 연다
            if (box != null && !box.path.isEmpty() && lineFacility != box.facility) {
                List<Double> here = toLatLng(f.geometry().coordinates().get(0));
                cumulative = flush(steps, box, cumulative);
                box = new Box();
                box.lat = here.get(0);
                box.lng = here.get(1);
                box.pointType = RouteStepType.FP;
                box.description = lineFacility.getMessage();
            }
            if (box == null) continue;

            box.facility = lineFacility;
            box.distance += p.distance();
            box.time     += p.time();
            appendPath(box.path, f.geometry().coordinates());
        }

        if (box != null) flush(steps, box, cumulative);
        return steps;
    }

    //step 생성을 위한 임시 박스
    private static final class Box {
        Double lat, lng;
        String description;
        Integer turnType;
        RouteStepType pointType;
        FacilityType facility = FacilityType.NORMAL;
        int distance = 0;
        int time = 0;
        List<List<Double>> path = new ArrayList<>();
    }

    private int flush(List<RouteStep> steps, Box box, int cumulative) {
        boolean empty = box.path.isEmpty();          // EP는 뒤 구간이 없다
        steps.add(new RouteStep(
                steps.size(), box.lat, box.lng,
                withRealDistance(box.description, box.distance),
                box.turnType, box.pointType, box.facility,
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
