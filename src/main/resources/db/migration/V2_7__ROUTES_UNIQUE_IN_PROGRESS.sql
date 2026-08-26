-- 피보호자당 진행 중 경로는 하나뿐이다.
-- RouteRepository.findByWardIdAndStatus 가 Optional 을 돌려주므로 두 행이 생기면
-- 그 뒤 지도·진행중 조회가 전부 예외로 죽는다. 자동 재탐색이 켜지면 같은 요청이
-- 겹쳐 들어올 수 있어 애플리케이션 잠금만으로는 부족하다.
CREATE UNIQUE INDEX uix_routes_ward_in_progress
    ON routes (ward_id) WHERE status = 'IN_PROGRESS' AND deleted_at IS NULL;
