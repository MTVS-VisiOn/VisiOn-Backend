package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;

import java.util.List;

/**
 * 대중교통 경로 후보 하나의 요약. 검색 응답은 이것의 리스트다.
 * <p>
 * 후보 정렬은 환승 적은 순 → 도보 짧은 순이고, service 0(운행 종료)이 하나라도 섞인 후보는
 * 목록에서 뺀다. 티맵은 정렬도 안 해주고 운행 종료 노선도 그대로 추천한다.
 */
public record TransitSummaryResponse(
        Integer index,               // 정렬·필터 후의 순번. Redis에서 후보를 꺼내는 키가 된다
        TransportMode mode,
        Integer totalDistance,       // m. leg distance 합과 안 맞으므로 이 값을 그대로 쓴다
        Integer totalTime,           // 초
        Integer totalWalkTime,       // 초
        Integer totalWalkDistance,   // m
        Integer transferCount,       // 환승 횟수. 도보는 안 센다
        Integer totalFare,           // 원
        List<LegSummary> legs,       // 무엇을 타는지. 이게 없으면 후보를 고를 수 없다
        String startingName,         // 출발지
        String startingAddress,  // 출발지 주소
        List<Double> startingCoordinate,   // 출발지 좌표
        String destinationName,      // 도착지
        String destinationAddress,     // 도착지 주소
        List<Double> destinationCoordinate // 도착지 좌표
) implements NavigationSummary {

    /**
     * 후보를 고르는 데 필요한 만큼만 담는다. 좌표·정류장 목록·도보 안내문은 report 쪽이다.
     * <p>
     * mode는 한글 라벨로 내보낸다 — 최종 출력이 TTS이고, pointType·facility를 라벨로 바꾼
     * 선례(§2-8)와 같은 이유다. 원문(WALK/BUS/SUBWAY)이 필요하면 report에서 꺼낸다.
     */
    public record LegSummary(
            String mode,          // "도보" "버스" "지하철" "고속/시외버스" "기차" "항공" "해운"
            String route,         // "신분당선" "마을:서초18" "KTX:서울-부산". 도보는 null
            List<String> routes,  // 대체 노선까지 전부. route + Lane 이고 Lane의 service 0은 뺀다.
            // "서초18, 400, 4432 중 먼저 오는 것을 타세요"가 된다. 도보는 빈 리스트
            String startName,     // 승차 정류장 / 도보 시작 지점
            String endName,       // 하차 정류장 / 도보 끝 지점
            Integer stationCount, // 정거장 수 = stations.size() - 1. 도보는 null
            Integer sectionTime,  // 초
            Integer distance      // m. 0인 도보 leg는 목록에서 빼야 한다(같은 정류장 환승)
    ) {}
}
