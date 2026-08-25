package mtvs.onvision.vision.location.repository;

import mtvs.onvision.vision.location.domain.LocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 위치 이력 조회용.
 * <p>
 * **저장은 여기서 하지 않는다.** 배치 INSERT는 `LocationHistoryJdbcRepository`가 맡는다 —
 * `ON CONFLICT DO NOTHING`을 JPQL로 표현할 수 없고, id가 IDENTITY라 Hibernate가 JDBC 배치를 끄기 때문이다.
 */
public interface LocationHistoryRepository extends JpaRepository<LocationHistory, Long> {
}
