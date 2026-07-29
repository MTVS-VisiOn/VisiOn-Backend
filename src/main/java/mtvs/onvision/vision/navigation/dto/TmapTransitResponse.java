package mtvs.onvision.vision.navigation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 대중교통 경로검색(POST /transit/routes) 응답.
 *
 * 최상위 키가 3종이고 셋이 같이 오지 않는다.
 *   metaData : 성공
 *   result   : API 층 오류. 11·12·13·14는 HTTP 200으로 온다
 *   error    : 게이트웨이 층 오류. 문서 에러표(11~32)에 없다. category "gw"가 표식
 * HTTP 상태코드만 봐서는 11·12를 못 잡으므로 metaData 유무로 먼저 갈라야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapTransitResponse(
        MetaData metaData,
        Result result,
        GatewayError error
) {

    /* ---------------- 오류 ---------------- */

    /** 11 출발/도착 가까움 · 12 출발 정류장 없음 · 13 도착 정류장 없음 · 14 기타 (전부 200)
     *  21 형식 오류 · 22 필수값 누락 · 23 서비스 지역 아님 · 24 타임머신 시각 오류 (400)
     *  31 응답 없음 · 32 기타 (500) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            Integer status,
            String message
    ) {}

    /** 인증 실패·한도 초과 등이 여기로 온다. 무료 한도 초과 시 id "429", code "QUOTA_EXCEEDED". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GatewayError(
            String id,
            String category,   // "gw"
            String code,
            String message
    ) {}

    /* ---------------- 성공 ---------------- */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetaData(
            RequestParameters requestParameters,
            Plan plan
    ) {}

    /** pathType별 결과 개수. 합이 itineraries 길이와 일치한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestParameters(
            Integer subwayCount,        // pathType 1
            Integer busCount,           // 2
            Integer subwayBusCount,     // 3
            Integer expressbusCount,    // 4
            Integer trainCount,         // 5
            Integer airplaneCount,      // 6
            Integer ferryCount,         // 7
            Integer wideareaRouteCount,
            String reqDttm              // yyyymmddhhmiss
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Plan(
            List<Itinerary> itineraries   // 경로 후보. 정렬 기준이 없으므로 우리가 정렬한다
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Itinerary(
            Fare fare,
            Integer totalTime,          // 초. leg sectionTime 합과 정확히 일치한다
            Integer totalDistance,      // m. leg distance 합과 안 맞는다(항상 초과, 최대 +691m).
            //    합산으로 검산하지 말고 이 값을 그대로 쓸 것
            Integer totalWalkTime,      // 초. 도보 leg sectionTime 합과 일치
            Integer totalWalkDistance,  // m.  도보 leg distance 합과 일치
            Integer transferCount,      // 환승 횟수. 이동수단 기준이고 도보는 안 센다
            Integer pathType,           // 1 지하철 2 버스 3 버스+지하철 4 고속/시외버스 5 기차 6 항공 7 해운
            List<Leg> legs
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fare(
            Regular regular
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Regular(
            Integer totalFare   // 광역 leg가 있으면 routePayment 합 + 시내 요금.
            // 시내 구간 요금은 leg별로 안 나온다
    ) {}

    /**
     * 공통 필드는 mode·sectionTime·distance·start·end 다섯뿐이고 나머지는 종류별로 있고 없다.
     *   도보(첫·마지막 leg) : steps
     *   도보(중간 환승)      : passShape만. steps가 아예 없다 — 규칙이다(16개 itinerary 전수 위반 0건)
     *   대중교통            : route·routeId·type·service·routeColor·passStopList·passShape
     *   광역(기차·항공·고속/시외버스) : routePayment. passShape이 출발-도착 직선 2점이다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(
            String mode,          // WALK BUS SUBWAY EXPRESSBUS TRAIN AIRPLANE FERRY
            Integer sectionTime,  // 초
            Integer distance,     // m. 0인 도보 leg가 나온다(같은 정류장 환승). 걸러야 한다
            Place start,
            Place end,

            /* 대중교통 leg만 */
            String route,         // "간선:400" "신분당선" "KTX:서울-부산" — 접두어가 type의 한글명
            String routeId,
            Integer type,         // 이동수단별 노선코드. 문서 지하철 표의 6은 5호선이 아니라 6호선이다
            String routeColor,    // # 없는 6자리 hex. type과 1:1이 아니다
            Integer service,      // 1 운행중 / 0 운행종료. 노선이 아니라 routeId 단위다.
            // 운행 종료 노선도 결과에서 안 빠지므로 우리가 걸러야 한다
            PassStopList passStopList,
            Integer routePayment, // 광역 이동수단 요금(원). 버스·지하철엔 없다

            /** 같은 구간을 대체할 수 있는 노선. 실물 키가 대문자 L이다.
             *  대표 노선(route)은 여기에 절대 포함되지 않는다 — 탈 수 있는 노선 = route + Lane 전부 */
            @JsonProperty("Lane") @JsonAlias("lane") List<Lane> lane,

            /* 도보 leg만 */
            List<Step> steps,

            /* 폴리라인 */
            PassShape passShape
    ) {}

    /** lon·lat이 숫자다. Station의 문자열과 타입도 값도 다르다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(
            String name,
            Double lon,
            Double lat
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PassStopList(
            @JsonAlias("stationList") List<Station> stations   // 문서 샘플의 stationList는 낡은 것. 실물은 stations
    ) {}

    /** index는 0부터고 승차 정류장을 포함한다. 정거장 수 = stations.size() - 1. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Station(
            Integer index,
            String stationID,
            String stationName,
            String lon,   // 문자열이다
            String lat    // 문자열이다
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lane(
            String route,
            String routeId,
            Integer type,
            String routeColor,
            Integer service   // 0이면 안내에서 빼야 한다
    ) {}

    /**
     * 도보 상세. 보행자 API와 달리 description의 거리가 step.distance와 전부 일치해서
     * withRealDistance가 필요 없다. facilityType이 없어 횡단보도·계단을 코드로는 못 잡는다.
     *
     * ⚠ Σsteps.distance != leg.distance 인 경우가 있다(11개 중 8개, 최대 405m).
     *   대중교통이 보행자 API의 LineString을 Point마다 하나씩만 쓰고 나머지를 버려서 생긴다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            String streetName,   // 빈 문자열로 오는 경우가 있다
            Integer distance,    // 문서는 double, 실물은 정수로만 왔다
            String description,  // 표준형 "우회전 후 23m 이동" / 축약형 "소월로, 429m"
            String linestring    // "경도,위도 경도,위도 ..." 공백 구분 문자열
    ) {}

    /** 보행자·자동차의 JSON 배열이 아니라 문자열이다. 파싱해서 [위도, 경도]로 뒤집어야 한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PassShape(
            String linestring
    ) {}
}
