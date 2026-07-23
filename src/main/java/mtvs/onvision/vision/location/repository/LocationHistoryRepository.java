package mtvs.onvision.vision.location.repository;

import mtvs.onvision.vision.location.domain.LocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationHistoryRepository extends JpaRepository<LocationHistory, Long> {
}
