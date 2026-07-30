package mtvs.onvision.vision.navigation.repository;

import mtvs.onvision.vision.navigation.domain.Route;
import mtvs.onvision.vision.navigation.domain.RouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {
    Optional<Route> findByWardIdAndStatus(Long wardId, RouteStatus status);
}
