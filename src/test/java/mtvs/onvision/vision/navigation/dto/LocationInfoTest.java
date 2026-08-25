package mtvs.onvision.vision.navigation.dto;

import mtvs.onvision.vision.navigation.domain.TransportMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocationInfo의")
class LocationInfoTest {

    private static final Double CENTER_LAT = 37.40094774;   // 판교박물관 중심점
    private static final Double CENTER_LON = 127.09565393;
    private static final Double ENTRANCE_LAT = 37.40075332; // 같은 POI의 보행자 입구점. 중심점과 26.2m
    private static final Double ENTRANCE_LON = 127.09582059;

    private LocationInfo destination(Double pnsLat, Double pnsLon) {
        return new LocationInfo("판교박물관", "판교박물관", CENTER_LAT, CENTER_LON,
                "경기 성남시 분당구 판교로 191", null, pnsLat, pnsLon);
    }

    @Nested
    @DisplayName("Describe: routingCoordinate 메서드는")
    class Describe_with_routingCoordinate {

        @Nested
        @DisplayName("Context: 보행자 입구점이 유효하고 걸어서 도착하면")
        class Context_with_entrance {

            @Test
            @DisplayName("(WALK)It : 중심점이 아니라 입구점을 [위도, 경도]로 돌려준다")
            void it_prefers_entrance() {
                //given&when&then : 중심점은 건물 안쪽이라 안내가 벽 앞에서 끝난다
                assertThat(destination(ENTRANCE_LAT, ENTRANCE_LON).routingCoordinate(TransportMode.WALK))
                        .containsExactly(ENTRANCE_LAT, ENTRANCE_LON);
            }

            @Test
            @DisplayName("(TRANSIT)It : 대중교통도 마지막 구간은 걸으므로 입구점을 쓴다")
            void it_prefers_entrance_for_transit() {
                //given&when&then
                assertThat(destination(ENTRANCE_LAT, ENTRANCE_LON).routingCoordinate(TransportMode.TRANSIT))
                        .containsExactly(ENTRANCE_LAT, ENTRANCE_LON);
            }
        }

        @Nested
        @DisplayName("Context: 자동차면")
        class Context_with_car {

            @Test
            @DisplayName("It : 입구점이 있어도 중심점으로 간다")
            void it_ignores_entrance_for_car() {
                //given : 주차장 POI의 입구점이 주차장이 아니라 본관 보행자 출입구를 가리킨다
                //        (실측 samples/poi-haengjeong — 자기 중심점에서 27~33m). 차를 댈 자리로는 틀리다
                assertThat(destination(ENTRANCE_LAT, ENTRANCE_LON).routingCoordinate(TransportMode.CAR))
                        .containsExactly(CENTER_LAT, CENTER_LON);
            }
        }

        @Nested
        @DisplayName("Context: 보행자 입구점이 없거나 좌표가 아니면")
        class Context_without_entrance {

            @Test
            @DisplayName("It : 중심점으로 폴백한다")
            void it_falls_back_to_center() {
                //given : 티맵 2025-05 추가분이라 옛 POI엔 아예 없다
                assertThat(destination(null, null).routingCoordinate(TransportMode.WALK))
                        .containsExactly(CENTER_LAT, CENTER_LON);
            }

            @Test
            @DisplayName("(0.0)It : 값 없음을 좌표로 쓰지 않는다")
            void it_rejects_zero() {
                //given : 티맵이 값 없음을 "0.0"으로 채우는 자리가 있다(nearPoiX/nearPoiY 실측)
                assertThat(destination(0.0, 0.0).routingCoordinate(TransportMode.WALK))
                        .containsExactly(CENTER_LAT, CENTER_LON);
            }

            @Test
            @DisplayName("(위경도 뒤바뀜)It : 한국 범위를 벗어나면 쓰지 않는다")
            void it_rejects_swapped_coordinate() {
                //given : 위도 127은 존재하지 않는다. 뒤집혀 들어오면 여기서 걸린다
                assertThat(destination(ENTRANCE_LON, ENTRANCE_LAT).routingCoordinate(TransportMode.WALK))
                        .containsExactly(CENTER_LAT, CENTER_LON);
            }
        }
    }

    @Nested
    @DisplayName("Describe: 입구점 없는 생성자는")
    class Describe_with_legacy_constructor {

        @Test
        @DisplayName("It : pns를 null로 두고 기존 필드는 그대로 채운다")
        void it_keeps_existing_fields() {
            //given&when : 조회 응답(NavigationResponse.from)이 쓰는 자리다
            LocationInfo info = new LocationInfo("판교박물관", "판교박물관", CENTER_LAT, CENTER_LON,
                    "경기 성남시 분당구 판교로 191", null);

            //then
            assertThat(info.pnsLat()).isNull();
            assertThat(info.pnsLon()).isNull();
            assertThat(info.routingCoordinate(TransportMode.WALK)).containsExactly(CENTER_LAT, CENTER_LON);
        }
    }
}
